package com.messaging.server.service;

import com.messaging.common.entities.User;
import com.messaging.common.enums.Role;
import com.messaging.common.enums.UserStatus;
import com.messaging.server.dao.UserDAO;
import org.mindrot.jbcrypt.BCrypt;

public class AuthService {

    private final UserDAO userDAO = new UserDAO();

    // Inscription (RG1, RG9)
    public User register(String username, String password, Role role) {
        if (userDAO.existsByUsername(username)) {
            throw new IllegalArgumentException("Ce nom d'utilisateur est déjà pris.");
        }
        String hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt());
        User user = new User(username, hashedPassword, role);
        return userDAO.save(user);
    }

    // Connexion (RG2, RG4, RG9)
    public User login(String username, String password) {
        User user = userDAO.findByUsername(username);
        if (user == null) {
            throw new IllegalArgumentException("Utilisateur introuvable.");
        }
        if (!BCrypt.checkpw(password, user.getPassword())) {
            throw new IllegalArgumentException("Mot de passe incorrect.");
        }
        userDAO.updateStatus(username, UserStatus.ONLINE);
        user.setStatus(UserStatus.ONLINE);
        return user;
    }

    // Déconnexion (RG4)
    public void logout(String username) {
        userDAO.updateStatus(username, UserStatus.OFFLINE);
    }
}