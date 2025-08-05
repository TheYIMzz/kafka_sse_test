package com.example.project.dto;

public class Event {
    private final String id;    // 이벤트 고유 ID
    private final String data;  // 전송할 페이로드

    public Event(String id, String data) {
        this.id = id;
        this.data = data;
    }

    public String getId() {
        return id;
    }

    public String getData() {
        return data;
    }
}
