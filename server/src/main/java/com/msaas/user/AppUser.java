package com.msaas.user;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document("users")
public class AppUser {
    @Id
    private String id;

    @Indexed(unique = true)
    private String email;

    @Indexed(unique = true, sparse = true)
    private String username;

    private String passwordHash;
    private SystemRole systemRole = SystemRole.USER;
    private boolean disabled;
    private Instant createdAt;

    public AppUser() {
    }

    public AppUser(String email, String passwordHash) {
        this(email, passwordHash, null);
    }

    public AppUser(String email, String passwordHash, String username) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.username = username;
        this.createdAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public SystemRole getSystemRole() {
        return systemRole == null ? SystemRole.USER : systemRole;
    }

    public void setSystemRole(SystemRole systemRole) {
        this.systemRole = systemRole == null ? SystemRole.USER : systemRole;
    }

    public boolean isDisabled() {
        return disabled;
    }

    public void setDisabled(boolean disabled) {
        this.disabled = disabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
