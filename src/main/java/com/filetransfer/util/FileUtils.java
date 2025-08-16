package com.filetransfer.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Utility class for file operations
 */
public class FileUtils {
    
    // 常见文件类型集合
    private static final Set<String> IMAGE_EXTENSIONS = new HashSet<>(
            Arrays.asList("jpg", "jpeg", "png", "gif", "bmp", "webp", "svg", "tiff"));
    
    private static final Set<String> DOCUMENT_EXTENSIONS = new HashSet<>(
            Arrays.asList("doc", "docx", "pdf", "txt", "rtf", "odt", "md", "html", "htm"));
    
    private static final Set<String> SPREADSHEET_EXTENSIONS = new HashSet<>(
            Arrays.asList("xls", "xlsx", "csv", "ods"));
    
    private static final Set<String> PRESENTATION_EXTENSIONS = new HashSet<>(
            Arrays.asList("ppt", "pptx", "odp"));
    
    private static final Set<String> ARCHIVE_EXTENSIONS = new HashSet<>(
            Arrays.asList("zip", "rar", "7z", "tar", "gz", "bz2"));
    
    private static final Set<String> EXECUTABLE_EXTENSIONS = new HashSet<>(
            Arrays.asList("exe", "msi", "apk", "app", "dmg", "deb", "rpm"));
    
    /**
     * 获取文件扩展名
     * 
     * @param fileName 文件名
     * @return 文件扩展名（小写）
     */
    public static String getFileExtension(String fileName) {
        if (fileName == null || fileName.isEmpty() || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
    }
    
    /**
     * 根据文件扩展名判断文件类型
     * 
     * @param fileName 文件名
     * @return 文件类型描述
     */
    public static String getFileTypeByExtension(String fileName) {
        String extension = getFileExtension(fileName);
        
        if (extension.isEmpty()) {
            return "Unknown";
        }
        
        if (IMAGE_EXTENSIONS.contains(extension)) {
            return "Image";
        } else if (DOCUMENT_EXTENSIONS.contains(extension)) {
            return "Document";
        } else if (SPREADSHEET_EXTENSIONS.contains(extension)) {
            return "Spreadsheet";
        } else if (PRESENTATION_EXTENSIONS.contains(extension)) {
            return "Presentation";
        } else if (ARCHIVE_EXTENSIONS.contains(extension)) {
            return "Archive";
        } else if (EXECUTABLE_EXTENSIONS.contains(extension)) {
            return "Executable";
        } else if (extension.equals("mp3") || extension.equals("wav") || extension.equals("ogg") || extension.equals("flac")) {
            return "Audio";
        } else if (extension.equals("mp4") || extension.equals("avi") || extension.equals("mkv") || extension.equals("mov")) {
            return "Video";
        } else {
            return "Other";
        }
    }
    
    /**
     * 创建目录（如果不存在）
     * 
     * @param directoryPath 目录路径
     * @return 是否成功创建
     */
    public static boolean createDirectoryIfNotExists(String directoryPath) {
        File directory = new File(directoryPath);
        if (!directory.exists()) {
            return directory.mkdirs();
        }
        return true;
    }
    
    /**
     * 格式化文件大小
     * 
     * @param size 文件大小（字节）
     * @return 格式化后的文件大小
     */
    public static String formatFileSize(long size) {
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
    
    /**
     * 获取MIME类型
     * 
     * @param file 文件
     * @return MIME类型
     */
    public static String getMimeType(File file) {
        try {
            Path path = Paths.get(file.getAbsolutePath());
            return Files.probeContentType(path);
        } catch (IOException e) {
            // 如果无法确定MIME类型，根据扩展名猜测
            String extension = getFileExtension(file.getName());
            
            if (IMAGE_EXTENSIONS.contains(extension)) {
                return "image/" + extension;
            } else if (extension.equals("pdf")) {
                return "application/pdf";
            } else if (extension.equals("txt")) {
                return "text/plain";
            } else if (extension.equals("html") || extension.equals("htm")) {
                return "text/html";
            } else if (extension.equals("mp3")) {
                return "audio/mpeg";
            } else if (extension.equals("mp4")) {
                return "video/mp4";
            } else if (extension.equals("zip")) {
                return "application/zip";
            } else if (extension.equals("doc") || extension.equals("docx")) {
                return "application/msword";
            } else if (extension.equals("xls") || extension.equals("xlsx")) {
                return "application/vnd.ms-excel";
            } else if (extension.equals("ppt") || extension.equals("pptx")) {
                return "application/vnd.ms-powerpoint";
            }
            
            return "application/octet-stream";
        }
    }

    /**
     * 根据文件名获取MIME类型
     * 
     * @param fileName 文件名
     * @return MIME类型
     */
    public static String getContentTypeByFileName(String fileName) {
        String extension = getFileExtension(fileName);
        
        if (IMAGE_EXTENSIONS.contains(extension)) {
            return "image/" + extension;
        } else if (extension.equals("pdf")) {
            return "application/pdf";
        } else if (extension.equals("txt")) {
            return "text/plain";
        } else if (extension.equals("html") || extension.equals("htm")) {
            return "text/html";
        } else if (extension.equals("mp3")) {
            return "audio/mpeg";
        } else if (extension.equals("wav")) {
            return "audio/wav";
        } else if (extension.equals("ogg")) {
            return "audio/ogg";
        } else if (extension.equals("mp4")) {
            return "video/mp4";
        } else if (extension.equals("avi")) {
            return "video/x-msvideo";
        } else if (extension.equals("mkv")) {
            return "video/x-matroska";
        } else if (extension.equals("mov")) {
            return "video/quicktime";
        } else if (extension.equals("zip")) {
            return "application/zip";
        } else if (extension.equals("rar")) {
            return "application/x-rar-compressed";
        } else if (extension.equals("7z")) {
            return "application/x-7z-compressed";
        } else if (extension.equals("doc")) {
            return "application/msword";
        } else if (extension.equals("docx")) {
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        } else if (extension.equals("xls")) {
            return "application/vnd.ms-excel";
        } else if (extension.equals("xlsx")) {
            return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        } else if (extension.equals("ppt")) {
            return "application/vnd.ms-powerpoint";
        } else if (extension.equals("pptx")) {
            return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
        } else if (extension.equals("csv")) {
            return "text/csv";
        } else if (extension.equals("json")) {
            return "application/json";
        } else if (extension.equals("xml")) {
            return "application/xml";
        } else if (extension.equals("js")) {
            return "application/javascript";
        } else if (extension.equals("css")) {
            return "text/css";
        }
        
        return "application/octet-stream";
    }
} 