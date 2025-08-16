package com.filetransfer.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.io.Serializable;
import java.util.Date;

/**
 * Model class representing file information
 */
public class FileInfo implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private String id;
    private String fileName;
    private String originalFileName;
    private String contentType;
    private long size;
    private String path;
    private Date uploadDate;
    private Date expiryDate;
    private String uploadedBy;
    private String status;
    private String checksum;
    private boolean isPublic;
    private int downloadCount;
    private String fileExtension;
    private String description;
    
    public FileInfo() {
    }
    
    public FileInfo(String fileName, String contentType, long size) {
        this.fileName = fileName;
        this.originalFileName = fileName;
        this.contentType = contentType;
        this.size = size;
        this.uploadDate = new Date();
        this.status = "UPLOADED";
        this.downloadCount = 0;
        this.isPublic = false;
        
        // Extract file extension
        if (fileName != null && fileName.contains(".")) {
            this.fileExtension = fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
        }
    }
    
    // Getters and Setters
    
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getFileName() {
        return fileName;
    }
    
    public void setFileName(String fileName) {
        this.fileName = fileName;
    }
    
    public String getOriginalFileName() {
        return originalFileName;
    }
    
    public void setOriginalFileName(String originalFileName) {
        this.originalFileName = originalFileName;
    }
    
    public String getContentType() {
        return contentType;
    }
    
    public void setContentType(String contentType) {
        this.contentType = contentType;
    }
    
    public long getSize() {
        return size;
    }
    
    public void setSize(long size) {
        this.size = size;
    }
    
    public String getPath() {
        return path;
    }
    
    public void setPath(String path) {
        this.path = path;
    }
    
    public Date getUploadDate() {
        return uploadDate;
    }
    
    public void setUploadDate(Date uploadDate) {
        this.uploadDate = uploadDate;
    }
    
    public Date getExpiryDate() {
        return expiryDate;
    }
    
    public void setExpiryDate(Date expiryDate) {
        this.expiryDate = expiryDate;
    }
    
    public String getUploadedBy() {
        return uploadedBy;
    }
    
    public void setUploadedBy(String uploadedBy) {
        this.uploadedBy = uploadedBy;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public String getChecksum() {
        return checksum;
    }
    
    public void setChecksum(String checksum) {
        this.checksum = checksum;
    }
    
    public boolean isPublic() {
        return isPublic;
    }
    
    public void setPublic(boolean isPublic) {
        this.isPublic = isPublic;
    }
    
    public int getDownloadCount() {
        return downloadCount;
    }
    
    public void setDownloadCount(int downloadCount) {
        this.downloadCount = downloadCount;
    }
    
    public String getFileExtension() {
        return fileExtension;
    }
    
    public void setFileExtension(String fileExtension) {
        this.fileExtension = fileExtension;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    // Helper methods
    
    @JsonIgnore
    public String getFormattedSize() {
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format("%.2f KB", size / 1024.0);
        } else if (size < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", size / (1024.0 * 1024));
        } else {
            return String.format("%.2f GB", size / (1024.0 * 1024 * 1024));
        }
    }
    
    public boolean isExpired() {
        if (expiryDate == null) {
            return false;
        }
        return new Date().after(expiryDate);
    }
    
    public String getFileType() {
        if (contentType == null) {
            return "Unknown";
        }
        
        if (contentType.startsWith("image/")) {
            return "Image";
        } else if (contentType.startsWith("video/")) {
            return "Video";
        } else if (contentType.startsWith("audio/")) {
            return "Audio";
        } else if (contentType.equals("application/pdf")) {
            return "PDF";
        } else if (contentType.equals("application/zip") || contentType.equals("application/x-rar-compressed")) {
            return "Archive";
        } else if (contentType.equals("application/msword") || 
                   contentType.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document")) {
            return "Document";
        } else if (contentType.equals("application/vnd.ms-excel") || 
                   contentType.equals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) {
            return "Spreadsheet";
        } else if (contentType.equals("application/vnd.ms-powerpoint") || 
                   contentType.equals("application/vnd.openxmlformats-officedocument.presentationml.presentation")) {
            return "Presentation";
        } else if (contentType.equals("application/x-msdownload") || contentType.equals("application/vnd.android.package-archive")) {
            return "Executable";
        } else if (contentType.equals("text/plain")) {
            return "Text";
        } else {
            return "Other";
        }
    }
    
    @Override
    public String toString() {
        return "FileInfo [id=" + id + ", fileName=" + fileName + ", contentType=" + contentType + ", size=" + size
                + ", uploadDate=" + uploadDate + ", status=" + status + "]";
    }
} 