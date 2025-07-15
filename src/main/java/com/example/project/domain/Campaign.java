
package com.example.project.domain;

public class Campaign {
    private Long id;
    private String userId;
    private String status;  // "N" or "Y"

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
