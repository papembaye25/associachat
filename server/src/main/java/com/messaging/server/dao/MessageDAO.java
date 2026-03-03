package com.messaging.server.dao;

import com.messaging.common.entities.Message;
import com.messaging.common.entities.User;
import com.messaging.common.enums.MessageStatus;
import com.messaging.server.HibernateUtil;
import jakarta.persistence.EntityManager;

import java.util.List;

public class MessageDAO {

    public Message save(Message message) {
        EntityManager em = HibernateUtil.getEntityManagerFactory().createEntityManager();
        try {
            em.getTransaction().begin();
            em.persist(message);
            em.getTransaction().commit();
            return message;
        } catch (Exception e) {
            em.getTransaction().rollback();
            throw e;
        } finally {
            em.close();
        }
    }

    // Historique entre deux utilisateurs (RG8 : ordre chronologique)
    public List<Message> findConversation(String user1, String user2) {
        EntityManager em = HibernateUtil.getEntityManagerFactory().createEntityManager();
        try {
            return em.createQuery(
                            "SELECT m FROM Message m " +
                                    "WHERE (m.sender.username = :u1 AND m.receiver.username = :u2) " +
                                    "OR (m.sender.username = :u2 AND m.receiver.username = :u1) " +
                                    "ORDER BY m.dateEnvoi ASC", Message.class)
                    .setParameter("u1", user1)
                    .setParameter("u2", user2)
                    .getResultList();
        } finally {
            em.close();
        }
    }

    // Messages en attente pour un utilisateur (RG6)
    public List<Message> findPendingMessages(String username) {
        EntityManager em = HibernateUtil.getEntityManagerFactory().createEntityManager();
        try {
            return em.createQuery(
                            "SELECT m FROM Message m " +
                                    "WHERE m.receiver.username = :username " +
                                    "AND m.statut = 'ENVOYE' " +
                                    "ORDER BY m.dateEnvoi ASC", Message.class)
                    .setParameter("username", username)
                    .getResultList();
        } finally {
            em.close();
        }
    }

    public void updateStatus(Long messageId, MessageStatus status) {
        EntityManager em = HibernateUtil.getEntityManagerFactory().createEntityManager();
        try {
            em.getTransaction().begin();
            Message msg = em.find(Message.class, messageId);
            if (msg != null) msg.setStatut(status);
            em.getTransaction().commit();
        } catch (Exception e) {
            em.getTransaction().rollback();
            throw e;
        } finally {
            em.close();
        }
    }
}