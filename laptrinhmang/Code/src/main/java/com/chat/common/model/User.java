package com.chat.common.model;

public class User {
    private int id;
    private String username;
    private String passwordHash;
    private String avatar;

    public User() {}

    public User(String username, String passwordHash, String avatar) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.avatar = avatar;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }
}