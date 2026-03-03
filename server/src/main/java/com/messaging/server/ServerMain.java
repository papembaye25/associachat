package com.messaging.server;

import com.messaging.common.dto.PacketType;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class ServerMain {

    private static final Logger logger = Logger.getLogger(ServerMain.class.getName());
    private static final int PORT = 5000;

    // Map des clients connectés (RG3, RG11)
    private final ConcurrentHashMap<String, ClientHandler> connectedClients
            = new ConcurrentHashMap<>();

    public void start() {
        logger.info("🚀 Serveur démarré sur le port " + PORT);

        // Forcer l'initialisation de Hibernate au démarrage
        try {
            HibernateUtil.getEntityManagerFactory();
            logger.info("✅ Base de données connectée et tables créées.");
        } catch (Exception e) {
            logger.severe("❌ Erreur base de données : " + e.getMessage());
            return;
        }

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                logger.info("📡 Nouvelle connexion : " + clientSocket.getInetAddress());
                ClientHandler handler = new ClientHandler(clientSocket, this);
                new Thread(handler).start();
            }
        } catch (IOException e) {
            logger.severe("Erreur serveur : " + e.getMessage());
        }
    }

    // Diffuser un événement à tous sauf l'expéditeur
    public void broadcast(String excludeUsername, PacketType type,
                          String key, String value) {
        connectedClients.forEach((username, handler) -> {
            if (!username.equals(excludeUsername)) {
                com.messaging.common.dto.Packet packet =
                        new com.messaging.common.dto.Packet(type);
                packet.put(key, value);
                handler.sendPacket(packet);
            }
        });
    }

    public void addClient(String username, ClientHandler handler) {
        connectedClients.put(username, handler);
    }

    public void removeClient(String username) {
        connectedClients.remove(username);
    }

    public ClientHandler getClient(String username) {
        return connectedClients.get(username);
    }

    public boolean isConnected(String username) {
        return connectedClients.containsKey(username);
    }

    public static void main(String[] args) {
        new ServerMain().start();
    }
}