package com.messaging.client.network;

import com.messaging.common.dto.Packet;
import com.messaging.common.dto.PacketType;

import java.io.*;
import java.net.Socket;
import java.util.function.Consumer;

public class ServerConnection {

    private static final String HOST = "localhost";
    private static final int PORT = 5000;

    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private Consumer<Packet> packetListener;
    private boolean running = false;

    public boolean connect() {
        try {
            socket = new Socket(HOST, PORT);
            out = new ObjectOutputStream(socket.getOutputStream());
            in = new ObjectInputStream(socket.getInputStream());
            running = true;
            startListening();
            return true;
        } catch (IOException e) {
            return false;
        }
    }


    private void startListening() {
        Thread listener = new Thread(() -> {
            try {
                while (running) {
                    Packet packet = (Packet) in.readObject();
                    if (packet != null && packetListener != null) {
                        packetListener.accept(packet);
                    }
                }
            } catch (IOException e) {
                // RG10 : perte de connexion
                if (packetListener != null) {
                    Packet error = new Packet(PacketType.ERROR);
                    error.put("message", "Connexion au serveur perdue.");
                    packetListener.accept(error);
                }
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            } finally {
                running = false;
            }
        });
        listener.setDaemon(true);
        listener.start();
    }

    public void sendPacket(Packet packet) {
        try {
            out.writeObject(packet);
            out.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void setPacketListener(Consumer<Packet> listener) {
        this.packetListener = listener;
    }

    public void disconnect() {
        running = false;
        try {
            if (socket != null) socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }
}