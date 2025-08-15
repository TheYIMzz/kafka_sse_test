package com.example.project.dto;

public class Event {
    private final String id;    // 이벤트 고유 ID
    private final String orgId;     // 이벤트가 발생한 하위조직 코드
    private final String data;  // 전송할 페이로드

    public Event(String id, String orgId, String data) {
        this.id = id;
        this.orgId = orgId;
        this.data = data;
    }

    public String getId() {
        return id;
    }

    public String getOrgId() {
        return orgId;
    }

    public String getData() {
        return data;
    }
}
