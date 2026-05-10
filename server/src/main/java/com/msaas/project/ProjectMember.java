package com.msaas.project;

import java.time.Instant;

public class ProjectMember {
    private String userId;
    private String email;
    private String username;
    private ProjectRole role;
    private Instant addedAt;

    public ProjectMember() {
    }

    public ProjectMember(String userId, String email, ProjectRole role) {
        this(userId, email, null, role);
    }

    public ProjectMember(String userId, String email, String username, ProjectRole role) {
        this.userId = userId;
        this.email = email;
        this.username = username;
        this.role = role;
        this.addedAt = Instant.now();
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public ProjectRole getRole() {
        return role;
    }

    public void setRole(ProjectRole role) {
        this.role = role;
    }

    public Instant getAddedAt() {
        return addedAt;
    }

    public void setAddedAt(Instant addedAt) {
        this.addedAt = addedAt;
    }
}
