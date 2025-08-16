package com.filetransfer.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.Date;

/**
 * Model class representing a user
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public class User implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private String id;
    private String username;
    
    // 使用WRITE_ONLY确保密码只在反序列化时被读取，不会在序列化时被包含
    // 这样可以防止密码在API响应中泄露
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;
    
    private String email;
    private String fullName;
    private Date createdDate;
    private Date lastLoginDate;
    private boolean enabled;
    private String role;
    private long totalStorageUsed;
    private long storageLimit;
    
    // 添加这个字段，与getFormattedStorageUsed方法对应
    @JsonProperty("formattedStorageUsed")
    private String formattedStorageUsed;
    
    public User() {
        this.createdDate = new Date();
        this.enabled = true;
        this.role = "USER";
        this.storageLimit = 1073741824; // 1GB default storage limit
        this.totalStorageUsed = 0;
    }
    
    public User(String username, String password, String email) {
        this();
        this.username = username;
        this.password = password;
        this.email = email;
    }
    
    // Getters and Setters
    
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getUsername() {
        return username;
    }
    
    public void setUsername(String username) {
        this.username = username;
    }
    
    public String getPassword() {
        return password;
    }
    
    public void setPassword(String password) {
        this.password = password;
    }
    
    public String getEmail() {
        return email;
    }
    
    public void setEmail(String email) {
        this.email = email;
    }
    
    public String getFullName() {
        return fullName;
    }
    
    public void setFullName(String fullName) {
        this.fullName = fullName;
    }
    
    public Date getCreatedDate() {
        return createdDate;
    }
    
    public void setCreatedDate(Date createdDate) {
        this.createdDate = createdDate;
    }
    
    public Date getLastLoginDate() {
        return lastLoginDate;
    }
    
    public void setLastLoginDate(Date lastLoginDate) {
        this.lastLoginDate = lastLoginDate;
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public String getRole() {
        return role;
    }
    
    public void setRole(String role) {
        this.role = role;
    }
    
    public long getTotalStorageUsed() {
        return totalStorageUsed;
    }
    
    public void setTotalStorageUsed(long totalStorageUsed) {
        this.totalStorageUsed = totalStorageUsed;
    }
    
    public long getStorageLimit() {
        return storageLimit;
    }
    
    public void setStorageLimit(long storageLimit) {
        this.storageLimit = storageLimit;
    }
    
    // 添加getter和setter
    public String getFormattedStorageUsed() {
        if (totalStorageUsed < 1024) {
            return totalStorageUsed + " B";
        } else if (totalStorageUsed < 1024 * 1024) {
            return String.format("%.2f KB", totalStorageUsed / 1024.0);
        } else if (totalStorageUsed < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", totalStorageUsed / (1024.0 * 1024));
        } else {
            return String.format("%.2f GB", totalStorageUsed / (1024.0 * 1024 * 1024));
        }
    }
    
    public void setFormattedStorageUsed(String formattedStorageUsed) {
        this.formattedStorageUsed = formattedStorageUsed;
    }
    
    // Helper methods
    
    public boolean hasStorageAvailable(long fileSize) {
        return (totalStorageUsed + fileSize) <= storageLimit;
    }
    
    /**
     * 检查密码是否为空或null
     */
    public boolean hasPassword() {
        return password != null && !password.isEmpty();
    }
    
    @JsonIgnore
    public String getFormattedStorageLimit() {
        if (storageLimit < 1024) {
            return storageLimit + " B";
        } else if (storageLimit < 1024 * 1024) {
            return String.format("%.2f KB", storageLimit / 1024.0);
        } else if (storageLimit < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", storageLimit / (1024.0 * 1024));
        } else {
            return String.format("%.2f GB", storageLimit / (1024.0 * 1024 * 1024));
        }
    }
    
    @JsonIgnore
    public double getStorageUsagePercentage() {
        if (storageLimit <= 0) {
            return 0;
        }
        return ((double) totalStorageUsed / storageLimit) * 100;
    }
    
    @Override
    public String toString() {
        return "User [id=" + id + ", username=" + username + ", email=" + email + ", role=" + role + "]";
    }
} 