package com.filetransfer.service;

import com.filetransfer.model.FileInfo;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Service for file operations
 */
public interface FileService {
    
    /**
     * Upload a file
     */
    FileInfo uploadFile(MultipartFile file, String userId) throws IOException;
    
    /**
     * Upload a chunk of a file
     */
    boolean uploadChunk(String fileId, MultipartFile chunk, int chunkNumber, int totalChunks, String userId) throws IOException;
    
    /**
     * Download a file
     */
    File downloadFile(String fileId) throws IOException;
    
    /**
     * Get file info
     */
    FileInfo getFileInfo(String fileId);
    
    /**
     * Get all files for a user
     */
    List<FileInfo> getUserFiles(String userId);
    
    /**
     * Delete a file
     */
    boolean deleteFile(String fileId, String userId);
    
    /**
     * Get file as stream
     */
    InputStream getFileAsStream(String fileId) throws IOException;
    
    /**
     * Update file info
     */
    FileInfo updateFileInfo(FileInfo fileInfo);
    
    /**
     * Search files
     */
    List<FileInfo> searchFiles(String query, String userId);
    
    /**
     * Calculate checksum
     */
    String calculateChecksum(String fileId) throws IOException;
    
    /**
     * Get public files
     */
    List<FileInfo> getPublicFiles();
    
    /**
     * Set file visibility
     */
    FileInfo setFileVisibility(String fileId, boolean isPublic, String userId);
    
    /**
     * Clean up expired files
     */
    int cleanupExpiredFiles();
    
    /**
     * Check if file is previewable
     */
    boolean isPreviewable(String fileId);
    
    /**
     * Get preview data for a file
     * @param fileId The file ID
     * @return A map containing preview data (type-specific)
     * @throws IOException If the file can't be read
     */
    java.util.Map<String, Object> getPreviewData(String fileId) throws IOException;
    
    /**
     * Get list of supported file types
     * @return List of supported MIME types or patterns
     */
    List<String> getSupportedFileTypes();
    
    /**
     * Check if file type is supported
     * @param contentType MIME type to check
     * @return true if supported, false otherwise
     */
    boolean isFileTypeSupported(String contentType);
    
    /**
     * 获取上传目录
     */
    String getUploadDirectory();
    
    /**
     * 重新加载文件信息
     */
    void reloadFileInfo();
    
    /**
     * 更新文件列表
     */
    void updateFileList(List<FileInfo> files);
    
    /**
     * 重置文件信息
     * 清空文件信息并重新扫描上传目录
     */
    boolean resetFileInfo();
} 