package com.docflow.collaboration;

import java.util.Map;

public class WsMessage {
    private String type;
    private Map<String, Object> data;

    public WsMessage() {
    }

    public WsMessage(String type, Map<String, Object> data) {
        this.type = type;
        this.data = data;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public void setData(Map<String, Object> data) {
        this.data = data;
    }
}
