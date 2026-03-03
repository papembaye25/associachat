package com.messaging.client.controllers;

import com.messaging.client.network.ServerConnection;
import com.messaging.common.dto.Packet;
import com.messaging.common.dto.PacketType;
import com.messaging.common.enums.Role;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

public class LoginController implements Initializable {

    @FXML private VBox loginForm;
    @FXML private VBox registerForm;
    @FXML private Button btnTabLogin;
    @FXML private Button btnTabRegister;

    @FXML private TextField loginUsername;
    @FXML private PasswordField loginPassword;
    @FXML private Label loginError;

    @FXML private TextField regUsername;
    @FXML private PasswordField regPassword;
    @FXML private ComboBox<Role> regRole;
    @FXML private Label registerMessage;

    private ServerConnection connection;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        regRole.getItems().addAll(Role.values());
        regRole.setValue(Role.MEMBRE);
        connection = new ServerConnection();
    }

    @FXML
    private void showLogin() {
        loginForm.setVisible(true);
        loginForm.setManaged(true);
        registerForm.setVisible(false);
        registerForm.setManaged(false);
        styleTabActive(btnTabLogin);
        styleTabInactive(btnTabRegister);
    }

    @FXML
    private void showRegister() {
        loginForm.setVisible(false);
        loginForm.setManaged(false);
        registerForm.setVisible(true);
        registerForm.setManaged(true);
        styleTabActive(btnTabRegister);
        styleTabInactive(btnTabLogin);
    }

    private void styleTabActive(Button btn) {
        btn.setStyle("-fx-background-color:#6C63FF; -fx-text-fill:white;" +
                "-fx-background-radius:10; -fx-font-weight:bold; -fx-cursor:hand;");
    }

    private void styleTabInactive(Button btn) {
        btn.setStyle("-fx-background-color:transparent; -fx-text-fill:#A0A0B0;" +
                "-fx-background-radius:10; -fx-cursor:hand;");
    }

    @FXML
    private void handleLogin() {
        String username = loginUsername.getText().trim();
        String password = loginPassword.getText();

        if (username.isEmpty() || password.isEmpty()) {
            showError(loginError, "Veuillez remplir tous les champs.");
            return;
        }

        showInfo(loginError, "Connexion en cours...");

        // Connexion au serveur
        if (!connection.isConnected()) {
            if (!connection.connect()) {
                showError(loginError, "Impossible de joindre le serveur.");
                return;
            }
        }

        // Écouter la réponse
        connection.setPacketListener(packet -> {
            Platform.runLater(() -> handleLoginResponse(packet, username));
        });

        // Envoyer le paquet LOGIN
        Packet packet = new Packet(PacketType.LOGIN);
        packet.put("username", username);
        packet.put("password", password);
        connection.sendPacket(packet);
    }

    private void handleLoginResponse(Packet packet, String username) {
        if (packet.getType() == PacketType.SUCCESS) {
            String role = packet.getString("role");
            openChatWindow(username, role, connection);
        } else if (packet.getType() == PacketType.ERROR) {
            showError(loginError, packet.getString("message"));
            connection.disconnect();
        }
    }

    @FXML
    private void handleRegister() {
        String username = regUsername.getText().trim();
        String password = regPassword.getText();
        Role role = regRole.getValue();

        if (username.isEmpty() || password.isEmpty() || role == null) {
            showMessage(registerMessage, "Veuillez remplir tous les champs.", false);
            return;
        }
        if (password.length() < 4) {
            showMessage(registerMessage, "Mot de passe trop court (min. 4 caractères).", false);
            return;
        }

        showMessage(registerMessage, "Inscription en cours...", true);

        if (!connection.isConnected()) {
            if (!connection.connect()) {
                showMessage(registerMessage, "Impossible de joindre le serveur.", false);
                return;
            }
        }

        connection.setPacketListener(packet -> {
            Platform.runLater(() -> {
                if (packet.getType() == PacketType.SUCCESS) {
                    showMessage(registerMessage, "✅ Inscription réussie ! Connectez-vous.", true);
                    connection.disconnect();
                    showLogin();
                } else {
                    showMessage(registerMessage, packet.getString("message"), false);
                    connection.disconnect();
                }
            });
        });

        Packet packet = new Packet(PacketType.REGISTER);
        packet.put("username", username);
        packet.put("password", password);
        packet.put("role", role.name());
        connection.sendPacket(packet);
    }

    private void showError(Label label, String message) {
        label.setText(message);
        label.setStyle("-fx-text-fill:#FF5252;");
    }

    private void showInfo(Label label, String message) {
        label.setText(message);
        label.setStyle("-fx-text-fill:#A0A0B0;");
    }

    private void showMessage(Label label, String message, boolean success) {
        label.setText(message);
        label.setStyle(success ? "-fx-text-fill:#4CAF50;" : "-fx-text-fill:#FF5252;");
    }

    private void openChatWindow(String username, String role,
                                ServerConnection connection) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/messaging/client/fxml/chat.fxml")
            );
            Parent root = loader.load();
            ChatController chatController = loader.getController();
            chatController.initUser(username, role, connection);

            Stage stage = (Stage) loginUsername.getScene().getWindow();
            Scene scene = new Scene(root);
            stage.setScene(scene);
            stage.setTitle("AssociaChat — " + username);
            stage.setWidth(950);
            stage.setHeight(680);
            stage.setResizable(true);
            stage.centerOnScreen();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}