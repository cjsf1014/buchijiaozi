package com.filetransfer.model;

import java.io.Serializable;
import java.util.Date;
import java.util.UUID;

/**
 * Model class representing a file transfer request
 */
public class TransferRequest implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private String id;
    private String fileId;
    private String senderId;
    private String recipientId;
    private String recipientEmail;
    private Date requestDate;
    private Date expiryDate;
    private String status;
    private String accessCode;
    private int downloadLimit;
    private int downloadCount;
    private boolean notifyOnDownload;
    private String message;
    
    public TransferRequest() {
        this.id = UUID.randomUUID().toString();
        this.requestDate = new Date();
        this.status = "PENDING";
        this.downloadCount = 0;
        this.downloadLimit = -1; // No limit by default
        this.notifyOnDownload = true;
        this.accessCode = generateAccessCode();
    }
    
    // Getters and Setters
    
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getFileId() {
        return fileId;
    }
    
    public void setFileId(String fileId) {
        this.fileId = fileId;
    }
    
    public String getSenderId() {
        return senderId;
    }
    
    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }
    
    public String getRecipientId() {
        return recipientId;
    }
    
    public void setRecipientId(String recipientId) {
        this.recipientId = recipientId;
    }
    
    public String getRecipientEmail() {
        return recipientEmail;
    }
    
    public void setRecipientEmail(String recipientEmail) {
        this.recipientEmail = recipientEmail;
    }
    
    public Date getRequestDate() {
        return requestDate;
    }
    
    public void setRequestDate(Date requestDate) {
        this.requestDate = requestDate;
    }
    
    public Date getExpiryDate() {
        return expiryDate;
    }
    
    public void setExpiryDate(Date expiryDate) {
        this.expiryDate = expiryDate;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public String getAccessCode() {
        return accessCode;
    }
    
    public void setAccessCode(String accessCode) {
        this.accessCode = accessCode;
    }
    
    public int getDownloadLimit() {
        return downloadLimit;
    }
    
    public void setDownloadLimit(int downloadLimit) {
        this.downloadLimit = downloadLimit;
    }
    
    public int getDownloadCount() {
        return downloadCount;
    }
    
    public void setDownloadCount(int downloadCount) {
        this.downloadCount = downloadCount;
    }
    
    public boolean isNotifyOnDownload() {
        return notifyOnDownload;
    }
    
    public void setNotifyOnDownload(boolean notifyOnDownload) {
        this.notifyOnDownload = notifyOnDownload;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    // Helper methods
    
    private String generateAccessCode() {
        // Generate a 6-digit access code
        return String.format("%06d", (int) (Math.random() * 1000000));
    }
    
    public boolean isExpired() {
        if (expiryDate == null) {
            return false;
        }
        return new Date().after(expiryDate);
    }
    
    public boolean hasReachedDownloadLimit() {
        if (downloadLimit < 0) {
            return false; // No limit
        }
        return downloadCount >= downloadLimit;
    }
    
    public boolean isActive() {
        return "ACTIVE".equals(status) && !isExpired() && !hasReachedDownloadLimit();
    }
    
    public String getDownloadUrl() {
        return "/api/files/download/" + id + "?code=" + accessCode;
    }
    
    @Override
    public String toString() {
        return "TransferRequest [id=" + id + ", fileId=" + fileId + ", status=" + status + ", requestDate=" + requestDate + "]";
    }
} 