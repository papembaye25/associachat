package com.messaging.common.dto;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class Packet implements Serializable {
    private static final long serialVersionUID = 1L;

    private PacketType type;
    private Map<String, Object> data = new HashMap<>();

    public Packet() {}

    public Packet(PacketType type) {
        this.type = type;
    }

    public void put(String key, Object value) {
        data.put(key, value);
    }

    public Object get(String key) {
        return data.get(key);
    }

    public String getString(String key) {
        Object val = data.get(key);
        return val != null ? val.toString() : null;
    }

    public PacketType getType() { return type; }
    public void setType(PacketType type) { this.type = type; }
    public Map<String, Object> getData() { return data; }
    public void setData(Map<String, Object> data) { this.data = data; }
}