package com.messaging.client.controllers;

import com.messaging.client.network.ServerConnection;
import com.messaging.common.dto.Packet;
import com.messaging.common.dto.PacketType;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.scene.layout.Priority;
import java.net.URL;
import java.util.ResourceBundle;

public class ChatController implements Initializable {

    @FXML private Label myUsernameLabel;
    @FXML private Label myRoleBadge;
    @FXML private Circle myStatusDot;
    @FXML private ListView<String> membersList;
    @FXML private Button btnAllMembers;

    @FXML private StackPane chatPlaceholder;
    @FXML private VBox chatActive;
    @FXML private Label chatHeaderName;
    @FXML private Label chatHeaderStatus;
    @FXML private Circle chatHeaderStatusDot;
    @FXML private Label chatAvatarLetter;

    @FXML private VBox messagesContainer;
    @FXML private ScrollPane messagesScroll;
    @FXML private TextField messageInput;

    private String currentUsername;
    private String currentUserRole;
    private String selectedMember;
    private ServerConnection connection;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        messagesContainer.heightProperty().addListener(
                (obs, oldVal, newVal) -> messagesScroll.setVvalue(1.0)
        );
        membersList.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldVal, newVal) -> {
                    if (newVal != null) openConversation(newVal);
                }
        );

        // Avatar coloré dans la liste des membres
        membersList.setCellFactory(listView -> new ListCell<String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                    setStyle("-fx-background-color: transparent;");
                    return;
                }

                String name = item.contains("(")
                        ? item.substring(0, item.lastIndexOf("(")).trim()
                        : item.trim();
                String role = item.contains("(")
                        ? item.replaceAll(".*\\((.*)\\).*", "$1")
                        : "";


                String[] colors = {"#6C63FF", "#FF6584", "#43B89C", "#FF9800",
                        "#2196F3", "#9C27B0", "#F44336", "#009688"};
                int colorIndex = Math.abs(name.charAt(0)) % colors.length;
                String avatarColor = colors[colorIndex];

                javafx.scene.layout.StackPane avatar = new javafx.scene.layout.StackPane();
                javafx.scene.shape.Circle circle = new javafx.scene.shape.Circle(18);
                circle.setFill(Color.web(avatarColor));
                Label initial = new Label(String.valueOf(name.charAt(0)).toUpperCase());
                initial.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 14px;");
                avatar.getChildren().addAll(circle, initial);

                // Infos membre
                VBox info = new VBox(2);
                Label nameLabel = new Label(name);
                nameLabel.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 13px;");
                Label roleLabel = new Label(role);
                roleLabel.setStyle("-fx-text-fill: #6C63FF; -fx-font-size: 10px; -fx-font-weight: bold;");
                info.getChildren().addAll(nameLabel, roleLabel);


                javafx.scene.shape.Circle statusDot = new javafx.scene.shape.Circle(4);
                statusDot.setFill(Color.web("#4CAF50"));
                statusDot.setStyle("-fx-effect: dropshadow(gaussian, #4CAF50, 6, 0.5, 0, 0);");

                HBox cell = new HBox(10);
                cell.setAlignment(Pos.CENTER_LEFT);
                cell.setPadding(new Insets(8, 10, 8, 10));

                javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);

                cell.getChildren().addAll(avatar, info, spacer, statusDot);

                // Style sélection
                if (isSelected()) {
                    cell.setStyle("-fx-background-color: rgba(108,99,255,0.25); -fx-background-radius: 10;");
                } else {
                    cell.setStyle("-fx-background-color: transparent; -fx-background-radius: 10;");
                    cell.setOnMouseEntered(e ->
                            cell.setStyle("-fx-background-color: rgba(108,99,255,0.15); -fx-background-radius: 10;")
                    );
                    cell.setOnMouseExited(e ->
                            cell.setStyle("-fx-background-color: transparent; -fx-background-radius: 10;")
                    );
                }

                setGraphic(cell);
                setText(null);
                setStyle("-fx-background-color: transparent; -fx-padding: 2 5 2 5;");
            }
        });
    }

    public void initUser(String username, String role, ServerConnection connection) {
        this.currentUsername = username;
        this.currentUserRole = role;
        this.connection = connection;

        myUsernameLabel.setText(username);
        styleBadge(myRoleBadge, role);

        if (role.equalsIgnoreCase("ORGANISATEUR")) {
            btnAllMembers.setVisible(true);
            btnAllMembers.setManaged(true);
        }

        connection.setPacketListener(packet ->
                Platform.runLater(() -> handlePacket(packet))
        );

        requestOnlineUsers();
    }

    private void handlePacket(Packet packet) {
        switch (packet.getType()) {
            case ONLINE_USERS_LIST -> updateMembersList(packet);
            case RECEIVE_MESSAGE -> receiveMessage(packet);
            case HISTORY_RESPONSE -> loadHistory(packet);
            case USER_CONNECTED -> onUserConnected(packet);
            case USER_DISCONNECTED -> onUserDisconnected(packet);
            case ALL_USERS_LIST -> showAllMembers(packet);
            case ERROR -> showError(packet.getString("message"));
            default -> {}
        }
    }

    @FXML
    private void handleGetAllMembers() {
        Packet packet = new Packet(PacketType.GET_ALL_USERS);
        connection.sendPacket(packet);
    }

    private void showAllMembers(Packet packet) {
        String usersData = packet.getString("users");
        messagesContainer.getChildren().clear();

        Label title = new Label("👥 Liste complète des membres");
        title.setStyle("-fx-text-fill:#6C63FF; -fx-font-size:16px; -fx-font-weight:bold;");
        HBox titleRow = new HBox(title);
        titleRow.setAlignment(Pos.CENTER);
        titleRow.setPadding(new Insets(10, 0, 15, 0));
        messagesContainer.getChildren().add(titleRow);

        if (usersData == null || usersData.isEmpty()) {
            Label empty = new Label("Aucun membre trouvé.");
            empty.setStyle("-fx-text-fill:#A0A0B0;");
            messagesContainer.getChildren().add(new HBox(empty));
            return;
        }

        String[] users = usersData.split(",");
        for (String user : users) {
            if (!user.isEmpty()) {
                String[] parts = user.split(":");
                if (parts.length >= 3) {
                    String username = parts[0];
                    String role = parts[1];
                    String status = parts[2];

                    String statusColor = status.equals("ONLINE") ? "#4CAF50" : "#757575";
                    String statusText = status.equals("ONLINE") ? "En ligne" : "Hors ligne";

                    HBox row = new HBox(12);
                    row.setAlignment(Pos.CENTER_LEFT);
                    row.setPadding(new Insets(8, 15, 8, 15));
                    row.setStyle("-fx-background-color:#16213E; -fx-background-radius:10;");

                    javafx.scene.shape.Circle dot = new javafx.scene.shape.Circle(5);
                    dot.setFill(Color.web(statusColor));

                    Label nameLabel = new Label(username);
                    nameLabel.setStyle("-fx-text-fill:white; -fx-font-weight:bold; -fx-font-size:14px;");

                    Label roleLabel = new Label(role);
                    roleLabel.setStyle("-fx-text-fill:#6C63FF; -fx-font-size:11px;");

                    javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
                    HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);

                    Label statusLabel = new Label(statusText);
                    statusLabel.setStyle("-fx-text-fill:" + statusColor + "; -fx-font-size:12px;");

                    row.getChildren().addAll(dot, nameLabel, roleLabel, spacer, statusLabel);

                    HBox wrapper = new HBox(row);
                    wrapper.setPadding(new Insets(3, 0, 3, 0));
                    HBox.setHgrow(row, javafx.scene.layout.Priority.ALWAYS);
                    messagesContainer.getChildren().add(wrapper);
                }
            }
        }
    }

    private void requestOnlineUsers() {
        Packet packet = new Packet(PacketType.GET_ONLINE_USERS);
        connection.sendPacket(packet);
    }

    private void updateMembersList(Packet packet) {
        String usersData = packet.getString("users");
        membersList.getItems().clear();
        if (usersData == null || usersData.isEmpty()) return;

        String[] users = usersData.split(",");
        for (String user : users) {
            if (!user.isEmpty()) {
                String[] parts = user.split(":");
                if (parts.length >= 2) {
                    membersList.getItems().add(parts[0] + " (" + parts[1] + ")");
                }
            }
        }
    }

    private String extractName(String memberEntry) {
        return memberEntry.contains("(")
                ? memberEntry.substring(0, memberEntry.lastIndexOf("(")).trim()
                : memberEntry.trim();
    }

    private void openConversation(String memberEntry) {
        String name = extractName(memberEntry);
        this.selectedMember = name;

        chatPlaceholder.setVisible(false);
        chatPlaceholder.setManaged(false);
        chatActive.setVisible(true);
        chatActive.setManaged(true);

        chatHeaderName.setText(name);
        chatAvatarLetter.setText(String.valueOf(name.charAt(0)).toUpperCase());
        chatHeaderStatus.setText("En ligne");
        chatHeaderStatus.setStyle("-fx-text-fill:#4CAF50;");
        chatHeaderStatusDot.setFill(Color.web("#4CAF50"));

        messagesContainer.getChildren().clear();

        Packet packet = new Packet(PacketType.GET_HISTORY);
        packet.put("otherUser", name);
        connection.sendPacket(packet);
    }

    private void loadHistory(Packet packet) {
        String historyData = packet.getString("history");
        messagesContainer.getChildren().clear();
        if (historyData == null || historyData.isEmpty()) return;

        String[] messages = historyData.split(";");
        for (String msg : messages) {
            if (!msg.isEmpty()) {
                String[] parts = msg.split("\\|");
                if (parts.length >= 3) {
                    String sender = parts[0];
                    String contenu = parts[1];
                    String time = parts[2];
                    boolean isSent = sender.equals(currentUsername);
                    addMessageBubble(contenu, isSent, time);
                }
            }
        }
    }

    private void receiveMessage(Packet packet) {
        String sender = packet.getString("sender");
        String contenu = packet.getString("contenu");
        String time = packet.getString("time");

        if (sender.equals(selectedMember)) {
            // Conversation déjà ouverte → afficher directement
            addMessageBubble(contenu, false, time);
        } else {

            for (String item : membersList.getItems()) {
                String itemName = extractName(item);
                if (itemName.equals(sender)) {
                    selectedMember = sender;
                    chatPlaceholder.setVisible(false);
                    chatPlaceholder.setManaged(false);
                    chatActive.setVisible(true);
                    chatActive.setManaged(true);
                    chatHeaderName.setText(sender);
                    chatAvatarLetter.setText(
                            String.valueOf(sender.charAt(0)).toUpperCase()
                    );
                    chatHeaderStatus.setText("En ligne");
                    chatHeaderStatus.setStyle("-fx-text-fill:#4CAF50;");
                    chatHeaderStatusDot.setFill(Color.web("#4CAF50"));
                    messagesContainer.getChildren().clear();
                    addMessageBubble(contenu, false, time);
                    break;
                }
            }
        }
    }

    private void onUserConnected(Packet packet) {
        String username = packet.getString("username");
        if (!username.equals(currentUsername)) {
            requestOnlineUsers();
            if (username.equals(selectedMember)) {
                chatHeaderStatus.setText("En ligne");
                chatHeaderStatus.setStyle("-fx-text-fill:#4CAF50;");
                chatHeaderStatusDot.setFill(Color.web("#4CAF50"));
            }
        }
    }

    private void onUserDisconnected(Packet packet) {
        String username = packet.getString("username");
        requestOnlineUsers();
        if (username.equals(selectedMember)) {
            chatHeaderStatus.setText("Hors ligne");
            chatHeaderStatus.setStyle("-fx-text-fill:#757575;");
            chatHeaderStatusDot.setFill(Color.web("#757575"));
        }
    }

    @FXML
    private void handleSendMessage() {
        String text = messageInput.getText().trim();
        if (text.isEmpty() || selectedMember == null) return;
        if (text.length() > 1000) {
            text = text.substring(0, 1000);
        }

        Packet packet = new Packet(PacketType.SEND_MESSAGE);
        packet.put("receiver", selectedMember);
        packet.put("contenu", text);
        connection.sendPacket(packet);

        addMessageBubble(text, true,
                java.time.LocalTime.now().format(
                        java.time.format.DateTimeFormatter.ofPattern("HH:mm")));
        messageInput.clear();
    }

    private void addMessageBubble(String text, boolean isSent, String time) {
        VBox bubble = new VBox(4);
        bubble.setMaxWidth(400);

        Text msgText = new Text(text);
        msgText.setWrappingWidth(340);
        msgText.setStyle("-fx-fill: white; -fx-font-size: 14px;");

        Label timeLabel = new Label(time);
        timeLabel.setStyle("-fx-text-fill: rgba(255,255,255,0.5); -fx-font-size:10px;");

        bubble.getChildren().addAll(msgText, timeLabel);
        bubble.getStyleClass().add(isSent ? "bubble-sent" : "bubble-received");
        bubble.setPadding(new Insets(10, 15, 10, 15));

        // Animation d'apparition
        bubble.setOpacity(0);
        bubble.setTranslateY(10);

        HBox row = new HBox();
        row.setPadding(new Insets(2, 0, 2, 0));
        row.setAlignment(isSent ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        row.getChildren().add(bubble);

        messagesContainer.getChildren().add(row);


        javafx.animation.FadeTransition fade =
                new javafx.animation.FadeTransition(
                        javafx.util.Duration.millis(200), bubble);
        fade.setFromValue(0);
        fade.setToValue(1);

        javafx.animation.TranslateTransition slide =
                new javafx.animation.TranslateTransition(
                        javafx.util.Duration.millis(200), bubble);
        slide.setFromY(10);
        slide.setToY(0);

        javafx.animation.ParallelTransition animation =
                new javafx.animation.ParallelTransition(fade, slide);
        animation.play();
    }

    private void showError(String message) {
        Platform.runLater(() -> {
            Label errorLabel = new Label("⚠️ " + message);
            errorLabel.setStyle("-fx-text-fill:#FF5252; -fx-font-size:12px;");
            HBox row = new HBox(errorLabel);
            row.setAlignment(Pos.CENTER);
            messagesContainer.getChildren().add(row);
        });
    }

    @FXML
    private void handleLogout() {
        Packet packet = new Packet(PacketType.LOGOUT);
        connection.sendPacket(packet);
        connection.disconnect();

        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                    getClass().getResource("/com/messaging/client/fxml/login.fxml")
            );
            javafx.scene.Parent root = loader.load();
            Stage stage = (Stage) myUsernameLabel.getScene().getWindow();
            javafx.scene.Scene scene = new javafx.scene.Scene(root, 500, 650);
            stage.setScene(scene);
            stage.setResizable(false);
            stage.setTitle("AssociaChat — Messagerie Association");
            stage.centerOnScreen();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void styleBadge(Label badge, String role) {
        switch (role.toUpperCase()) {
            case "ORGANISATEUR" -> {
                badge.setText("ORGANISATEUR");
                badge.getStyleClass().add("badge-organisateur");
            }
            case "BENEVOLE" -> {
                badge.setText("BÉNÉVOLE");
                badge.getStyleClass().add("badge-benevole");
            }
            default -> {
                badge.setText("MEMBRE");
                badge.getStyleClass().add("badge-membre");
            }
        }
    }
}