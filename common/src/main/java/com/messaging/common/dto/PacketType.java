package com.messaging.common.dto;

public enum PacketType {
    // Auth
    LOGIN, LOGOUT, REGISTER,
    // Réponses serveur
    SUCCESS, ERROR,
    // Messagerie
    SEND_MESSAGE, RECEIVE_MESSAGE,
    // Listes
    GET_ONLINE_USERS, ONLINE_USERS_LIST,
    GET_ALL_USERS, ALL_USERS_LIST,
    GET_HISTORY, HISTORY_RESPONSE,
    // Statut
    USER_CONNECTED, USER_DISCONNECTED
}