package com.messaging.server;

import com.messaging.common.dto.Packet;
import com.messaging.common.dto.PacketType;
import com.messaging.common.entities.Message;
import com.messaging.common.entities.User;
import com.messaging.common.enums.Role;
import com.messaging.common.enums.UserStatus;
import com.messaging.server.dao.UserDAO;
import com.messaging.server.service.AuthService;
import com.messaging.server.service.MessageService;
import com.google.gson.Gson;

import java.io.*;
import java.net.Socket;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.logging.Logger;

public class ClientHandler implements Runnable {

    private static final Logger logger = Logger.getLogger(ClientHandler.class.getName());
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("HH:mm");

    private final Socket socket;
    private final ServerMain server;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private String username;

    private final AuthService authService = new AuthService();
    private final MessageService messageService = new MessageService();
    private final UserDAO userDAO = new UserDAO();
    private final Gson gson = new Gson();

    public ClientHandler(Socket socket, ServerMain server) {
        this.socket = socket;
        this.server = server;
    }

    @Override
    public void run() {
        try {
            out = new ObjectOutputStream(socket.getOutputStream());
            in = new ObjectInputStream(socket.getInputStream());

            Packet packet;
            while ((packet = (Packet) in.readObject()) != null) {
                handlePacket(packet);
            }
        } catch (IOException e) {
            // Connexion fermée (RG10)
        } catch (ClassNotFoundException e) {
            logger.severe("Erreur désérialisation : " + e.getMessage());
        } finally {
            disconnect();
        }
    }

    private void handlePacket(Packet packet) {
        switch (packet.getType()) {
            case LOGIN -> handleLogin(packet);
            case REGISTER -> handleRegister(packet);
            case SEND_MESSAGE -> handleSendMessage(packet);
            case GET_ONLINE_USERS -> handleGetOnlineUsers();
            case GET_ALL_USERS -> handleGetAllUsers(packet);
            case GET_HISTORY -> handleGetHistory(packet);
            case LOGOUT -> disconnect();
            default -> sendError("Type de paquet inconnu.");
        }
    }

    private void handleLogin(Packet packet) {
        String uname = packet.getString("username");
        String pass = packet.getString("password");
        try {
            // RG3 : une seule session
            if (server.isConnected(uname)) {
                sendError("Cet utilisateur est déjà connecté.");
                return;
            }
            User user = authService.login(uname, pass);
            this.username = user.getUsername();
            server.addClient(username, this);

            Packet response = new Packet(PacketType.SUCCESS);
            response.put("username", user.getUsername());
            response.put("role", user.getRole().name());
            sendPacket(response);

            deliverPendingMessages();


            server.broadcast(username, PacketType.USER_CONNECTED, "username", username);
            logger.info("Connexion : " + username); // RG12

        } catch (Exception e) {
            sendError(e.getMessage());
        }
    }

    private void handleRegister(Packet packet) {
        String uname = packet.getString("username");
        String pass = packet.getString("password");
        String roleStr = packet.getString("role");
        try {
            Role role = Role.valueOf(roleStr);
            authService.register(uname, pass, role);
            Packet response = new Packet(PacketType.SUCCESS);
            response.put("message", "Inscription réussie !");
            sendPacket(response);
            logger.info("Inscription : " + uname); // RG12
        } catch (Exception e) {
            sendError(e.getMessage());
        }
    }

    private void handleSendMessage(Packet packet) {
        String receiver = packet.getString("receiver");
        String contenu = packet.getString("contenu");
        try {
            Message msg = messageService.sendMessage(username, receiver, contenu);


            ClientHandler receiverHandler = server.getClient(receiver);
            if (receiverHandler != null) {
                Packet delivery = new Packet(PacketType.RECEIVE_MESSAGE);
                delivery.put("sender", username);
                delivery.put("contenu", contenu);
                delivery.put("time", msg.getDateEnvoi().format(FMT));
                delivery.put("messageId", msg.getId());
                receiverHandler.sendPacket(delivery);
            }

            Packet confirm = new Packet(PacketType.SUCCESS);
            confirm.put("message", "Message envoyé.");
            confirm.put("messageId", msg.getId());
            sendPacket(confirm);
            logger.info("Message de " + username + " vers " + receiver); // RG12

        } catch (Exception e) {
            sendError(e.getMessage());
        }
    }

    private void handleGetOnlineUsers() {
        List<User> onlineUsers = userDAO.findAllOnline();
        Packet response = new Packet(PacketType.ONLINE_USERS_LIST);
        StringBuilder sb = new StringBuilder();
        for (User u : onlineUsers) {
            if (!u.getUsername().equals(username)) {
                sb.append(u.getUsername())
                        .append(":").append(u.getRole().name())
                        .append(",");
            }
        }
        response.put("users", sb.toString());
        sendPacket(response);
    }

    private void handleGetAllUsers(Packet packet) {
        // RG13 : seulement ORGANISATEUR
        User me = userDAO.findByUsername(username);
        if (me == null || me.getRole() != com.messaging.common.enums.Role.ORGANISATEUR) {
            sendError("Accès refusé. Réservé aux organisateurs.");
            return;
        }
        List<User> allUsers = userDAO.findAll();
        Packet response = new Packet(PacketType.ALL_USERS_LIST);
        StringBuilder sb = new StringBuilder();
        for (User u : allUsers) {
            sb.append(u.getUsername())
                    .append(":").append(u.getRole().name())
                    .append(":").append(u.getStatus().name())
                    .append(",");
        }
        response.put("users", sb.toString());
        sendPacket(response);
    }

    private void handleGetHistory(Packet packet) {
        String otherUser = packet.getString("otherUser");
        List<Message> history = messageService.getHistory(username, otherUser);
        Packet response = new Packet(PacketType.HISTORY_RESPONSE);
        StringBuilder sb = new StringBuilder();
        for (Message m : history) {
            sb.append(m.getSender().getUsername())
                    .append("|").append(m.getContenu())
                    .append("|").append(m.getDateEnvoi().format(FMT))
                    .append("|").append(m.getId())
                    .append(";");
        }
        response.put("history", sb.toString());
        sendPacket(response);
    }

    private void deliverPendingMessages() {
        List<Message> pending = messageService.getPendingMessages(username);
        for (Message msg : pending) {
            Packet delivery = new Packet(PacketType.RECEIVE_MESSAGE);
            delivery.put("sender", msg.getSender().getUsername());
            delivery.put("contenu", msg.getContenu());
            delivery.put("time", msg.getDateEnvoi().format(FMT));
            delivery.put("messageId", msg.getId());
            sendPacket(delivery);
        }
    }

    public void sendPacket(Packet packet) {
        try {
            out.writeObject(packet);
            out.flush();
        } catch (IOException e) {
            logger.warning("Erreur envoi paquet : " + e.getMessage());
        }
    }

    private void sendError(String message) {
        Packet error = new Packet(PacketType.ERROR);
        error.put("message", message);
        sendPacket(error);
    }

    private void disconnect() {
        try {
            if (username != null) {
                new AuthService().logout(username);
                server.removeClient(username);
                server.broadcast(username, PacketType.USER_DISCONNECTED, "username", username);
                logger.info("Déconnexion : " + username); // RG12
            }
            socket.close();
        } catch (IOException e) {
            logger.warning("Erreur déconnexion : " + e.getMessage());
        }
    }

    public String getUsername() { return username; }
}