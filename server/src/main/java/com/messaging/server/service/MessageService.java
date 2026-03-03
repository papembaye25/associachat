package com.messaging.server.service;

import com.messaging.common.entities.Message;
import com.messaging.common.entities.User;
import com.messaging.common.enums.MessageStatus;
import com.messaging.server.dao.MessageDAO;
import com.messaging.server.dao.UserDAO;

import java.util.List;

public class MessageService {

    private final MessageDAO messageDAO = new MessageDAO();
    private final UserDAO userDAO = new UserDAO();

    // Envoyer un message (RG5, RG6, RG7)
    public Message sendMessage(String senderUsername,
                               String receiverUsername,
                               String contenu) {
        // RG7 : contenu non vide
        if (contenu == null || contenu.trim().isEmpty()) {
            throw new IllegalArgumentException("Le message ne peut pas être vide.");
        }
        // RG7 : max 1000 caractères
        if (contenu.length() > 1000) {
            throw new IllegalArgumentException("Message trop long (max 1000 caractères).");
        }
        // RG5 : destinataire doit exister
        User receiver = userDAO.findByUsername(receiverUsername);
        if (receiver == null) {
            throw new IllegalArgumentException("Destinataire introuvable.");
        }
        User sender = userDAO.findByUsername(senderUsername);

        Message message = new Message(sender, receiver, contenu.trim());
        return messageDAO.save(message);
    }

    // Historique (RG8)
    public List<Message> getHistory(String user1, String user2) {
        return messageDAO.findConversation(user1, user2);
    }

    // Messages en attente (RG6)
    public List<Message> getPendingMessages(String username) {
        return messageDAO.findPendingMessages(username);
    }

    public void markAsRead(Long messageId) {
        messageDAO.updateStatus(messageId, MessageStatus.LU);
    }
}