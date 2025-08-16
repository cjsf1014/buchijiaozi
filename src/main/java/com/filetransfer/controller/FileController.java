package com.filetransfer.controller;

import com.filetransfer.model.ApiResponse;
import com.filetransfer.model.FileInfo;
import com.filetransfer.model.User;
import com.filetransfer.service.FileService;
import com.filetransfer.service.UserService;
import com.filetransfer.util.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;
import java.util.Base64;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Collections;
import org.springframework.http.ContentDisposition;
import java.io.EOFException;
import java.io.FileWriter;
import java.util.Date;

/**
 * REST Controller for file operations
 */
@RestController
@RequestMapping("/api/files")
public class FileController {
    
    private static final Logger logger = LoggerFactory.getLogger(FileController.class);
    
    @Autowired
    private FileService fileService;
    
    @Autowired
    private UserService userService;
    
    // 修改为手动创建
    private JwtUtil jwtUtil;
    
    @PostConstruct
    public void init() {
        // 手动创建JwtUtil实例
        jwtUtil = new JwtUtil();
        
        try {
            // 尝试从application.properties加载配置
            Properties props = PropertiesLoaderUtils.loadProperties(new ClassPathResource("application.properties"));
            String secret = props.getProperty("jwt.secret", "defaultSecretKeyThatIsLongEnoughForHmacSHA256Algorithm");
            String expirationStr = props.getProperty("jwt.expiration", "86400000");
            long expiration = Long.parseLong(expirationStr);
            
            jwtUtil.setSecret(secret);
            jwtUtil.setExpiration(expiration);
            
            // 初始化JwtUtil
            jwtUtil.init();
            
            logger.info("JwtUtil initialized in FileController");
        } catch (Exception e) {
            logger.warn("Failed to load application.properties in FileController, using default values", e);
            // 使用默认值
            jwtUtil.setSecret("defaultSecretKeyThatIsLongEnoughForHmacSHA256Algorithm");
            jwtUtil.setExpiration(86400000L); // 24小时
            jwtUtil.init();
        }
    }
    
    /**
     * Upload a file
     */
    @PostMapping("/upload")
    public ResponseEntity<ApiResponse> uploadFile(
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request) {
        
        logger.info("收到文件上传请求: 文件名={}, 大小={}, 内容类型={}", 
                file.getOriginalFilename(), file.getSize(), file.getContentType());
            
            // 记录更多调试信息
            logger.debug("文件上传详细信息 - 原始文件名字节: {}", 
                file.getOriginalFilename() != null ? 
                java.util.Arrays.toString(file.getOriginalFilename().getBytes()) : "null");
        
        try {
            String userId = getUserIdFromRequest(request);
            if (userId == null) {
                logger.warn("文件上传失败: 未授权的请求");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error("Unauthorized"));
            }
            
            if (file.isEmpty()) {
                logger.warn("文件上传失败: 文件为空");
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("File is empty"));
            }
            
            // 移除文件大小限制检查
            // long maxFileSize = 500 * 1024 * 1024; // 500MB
            // if (file.getSize() > maxFileSize) {
            //     logger.warn("文件上传失败: 文件大小超过限制 {} > {}", file.getSize(), maxFileSize);
            //     return ResponseEntity.badRequest()
            //             .body(ApiResponse.error("File size exceeds maximum limit of 500MB"));
            // }
            
            FileInfo fileInfo = fileService.uploadFile(file, userId);
            
            logger.info("文件上传成功: id={}, 文件名={}, 路径={}", 
                    fileInfo.getId(), fileInfo.getFileName(), fileInfo.getPath());
            
            return ResponseEntity.ok(ApiResponse.success("File uploaded successfully", fileInfo));
        } catch (IOException e) {
            logger.error("文件上传失败: {}", e.getMessage(), e);
            // 添加更详细的错误信息
            String errorMessage = "Failed to upload file: " + e.getMessage();
            if (e instanceof EOFException || e.getCause() instanceof EOFException) {
                errorMessage = "Connection interrupted during file upload. Please try again.";
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(errorMessage));
        } catch (IllegalArgumentException e) {
            logger.warn("文件上传失败: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            logger.error("文件上传失败(未预期的错误): {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("An unexpected error occurred: " + e.getMessage()));
        }
    }
    
    /**
     * Upload a file in chunks
     */
    @PostMapping("/upload/chunk")
    public ResponseEntity<ApiResponse> uploadChunk(
            @RequestParam("fileId") String fileId,
            @RequestParam("chunk") MultipartFile chunk,
            @RequestParam("chunkNumber") int chunkNumber,
            @RequestParam("totalChunks") int totalChunks,
            HttpServletRequest request) {
        
        try {
            String userId = getUserIdFromRequest(request);
            if (userId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error("Unauthorized"));
            }
            
            if (chunk.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("Chunk is empty"));
            }
            
            boolean isComplete = fileService.uploadChunk(fileId, chunk, chunkNumber, totalChunks, userId);
            
            if (isComplete) {
                FileInfo fileInfo = fileService.getFileInfo(fileId);
                return ResponseEntity.ok(ApiResponse.success("File upload complete", fileInfo));
            } else {
                return ResponseEntity.ok(ApiResponse.success("Chunk uploaded successfully", 
                        new ChunkResponse(fileId, chunkNumber, totalChunks)));
            }
        } catch (IOException e) {
            logger.error("Failed to upload chunk", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to upload chunk: " + e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }
    
    /**
     * Download a file
     */
    @GetMapping("/download/{fileId}")
    public ResponseEntity<?> downloadFile(
            @PathVariable("fileId") String fileId,
            @RequestParam(value = "token", required = false) String tokenParam,
            HttpServletRequest request) {
        
        try {
            // 从请求中获取用户ID，同时支持header和query参数中的token
            String token = tokenParam;
            if (token == null) {
                String authHeader = request.getHeader("Authorization");
                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                    token = authHeader.substring(7);
                }
            }
            
            String userId = null;
            if (token != null) {
                try {
                    userId = jwtUtil.getUserIdFromToken(token);
                } catch (Exception e) {
                    logger.error("Invalid token", e);
                }
            }
            
            if (userId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error("Unauthorized"));
            }
            
            FileInfo fileInfo = fileService.getFileInfo(fileId);
            if (fileInfo == null) {
                return ResponseEntity.notFound().build();
            }
            
            // Check if user is the owner or file is public
            if (!fileInfo.getUploadedBy().equals(userId) && !fileInfo.isPublic()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(ApiResponse.error("Access denied"));
            }
            
            File file = fileService.downloadFile(fileId);
            InputStream inputStream = new FileInputStream(file);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(fileInfo.getContentType()));
            
            // 正确处理中文文件名
            String filename = fileInfo.getOriginalFileName();
            String userAgent = request.getHeader("User-Agent");
            
            // 处理文件名编码，确保中文文件名正确显示
            if (filename != null) {
                try {
                    // 检查文件名编码并尝试修复
                    String decodedFilename = filename;
                    
                    // 尝试检测编码问题
                    byte[] bytes = filename.getBytes("ISO-8859-1");
                    // 检查是否包含非ASCII字符（负数）
                    boolean hasNonAscii = false;
                    for (byte b : bytes) {
                        if (b < 0) {
                            hasNonAscii = true;
                            break;
                        }
                    }
                    
                    // 如果有非ASCII字符，尝试用UTF-8解码
                    if (hasNonAscii) {
                        decodedFilename = new String(bytes, "UTF-8");
                        logger.debug("修复文件名编码: {} -> {}", filename, decodedFilename);
                    }
                    
                    filename = decodedFilename;
                } catch (Exception e) {
                    logger.warn("处理文件名编码时出错，使用原始文件名: {}", e.getMessage());
                }
            }
            
            // 针对不同浏览器使用不同的编码方式
            if (userAgent != null && userAgent.contains("MSIE")) {
                // IE浏览器
                filename = URLEncoder.encode(filename, "UTF-8");
                filename = filename.replace("+", "%20");
            } else if (userAgent != null && userAgent.contains("Firefox")) {
                // Firefox浏览器
                filename = "=?UTF-8?B?" + Base64.getEncoder().encodeToString(filename.getBytes(StandardCharsets.UTF_8)) + "?=";
            } else {
                // Chrome, Safari等其他浏览器
                filename = URLEncoder.encode(filename, "UTF-8");
            }
            
            headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
            headers.setContentLength(file.length());
            
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(new InputStreamResource(inputStream));
        } catch (IOException e) {
            logger.error("Failed to download file", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to download file: " + e.getMessage()));
        }
    }
    
    /**
     * Get file information
     */
    @GetMapping("/{fileId}")
    public ResponseEntity<ApiResponse> getFileInfo(
            @PathVariable("fileId") String fileId,
            @RequestParam(value = "token", required = false) String tokenParam,
            HttpServletRequest request) {
        
        // 从请求中获取用户ID，同时支持header和query参数中的token
        String token = tokenParam;
        if (token == null) {
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                token = authHeader.substring(7);
            }
        }
        
        String userId = null;
        if (token != null) {
            try {
                userId = jwtUtil.getUserIdFromToken(token);
            } catch (Exception e) {
                logger.error("Invalid token", e);
            }
        }
        
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Unauthorized"));
        }
        
        FileInfo fileInfo = fileService.getFileInfo(fileId);
        if (fileInfo == null) {
            return ResponseEntity.notFound().build();
        }
        
        // Check if user is the owner or file is public
        if (!fileInfo.getUploadedBy().equals(userId) && !fileInfo.isPublic()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Access denied"));
        }
        
        return ResponseEntity.ok(ApiResponse.success("File info retrieved successfully", fileInfo));
    }
    
    /**
     * 调试用 - 获取文件的原始信息（包括字节表示）
     */
    @GetMapping("/{fileId}/debug")
    public ResponseEntity<ApiResponse> getFileDebugInfo(
            @PathVariable("fileId") String fileId,
            HttpServletRequest request) {
        
        String userId = getUserIdFromRequest(request);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Unauthorized"));
        }
        
        FileInfo fileInfo = fileService.getFileInfo(fileId);
        if (fileInfo == null) {
            return ResponseEntity.notFound().build();
        }
        
        // Check if user is the owner or file is public
        if (!fileInfo.getUploadedBy().equals(userId) && !fileInfo.isPublic()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Access denied"));
        }
        
        // 创建调试信息
        Map<String, Object> debugInfo = new HashMap<>();
        debugInfo.put("id", fileInfo.getId());
        debugInfo.put("fileName", fileInfo.getFileName());
        debugInfo.put("originalFileName", fileInfo.getOriginalFileName());
        debugInfo.put("contentType", fileInfo.getContentType());
        debugInfo.put("fileType", fileInfo.getFileType());
        debugInfo.put("size", fileInfo.getSize());
        debugInfo.put("formattedSize", fileInfo.getFormattedSize());
        debugInfo.put("path", fileInfo.getPath());
        
        // 获取文件名的字节表示
        if (fileInfo.getFileName() != null) {
            byte[] filenameBytes = fileInfo.getFileName().getBytes();
            StringBuilder bytesHex = new StringBuilder();
            for (byte b : filenameBytes) {
                bytesHex.append(String.format("%02X ", b));
            }
            debugInfo.put("fileNameBytes", bytesHex.toString());
        }
        
        if (fileInfo.getOriginalFileName() != null) {
            byte[] originalFilenameBytes = fileInfo.getOriginalFileName().getBytes();
            StringBuilder bytesHex = new StringBuilder();
            for (byte b : originalFilenameBytes) {
                bytesHex.append(String.format("%02X ", b));
            }
            debugInfo.put("originalFileNameBytes", bytesHex.toString());
        }
        
        // 尝试不同编码解析
        try {
            if (fileInfo.getFileName() != null) {
                debugInfo.put("fileNameISO", new String(fileInfo.getFileName().getBytes("ISO-8859-1"), "UTF-8"));
                debugInfo.put("fileNameGBK", new String(fileInfo.getFileName().getBytes("ISO-8859-1"), "GBK"));
            }
            
            if (fileInfo.getOriginalFileName() != null) {
                debugInfo.put("originalFileNameISO", new String(fileInfo.getOriginalFileName().getBytes("ISO-8859-1"), "UTF-8"));
                debugInfo.put("originalFileNameGBK", new String(fileInfo.getOriginalFileName().getBytes("ISO-8859-1"), "GBK"));
            }
        } catch (Exception e) {
            logger.warn("转换编码失败", e);
        }
        
        return ResponseEntity.ok(ApiResponse.success("File debug info retrieved", debugInfo));
    }
    
    /**
     * 调试端点 - 获取文件名编码信息
     */
    @GetMapping("/{fileId}/filename-debug")
    public ResponseEntity<ApiResponse> getFilenameDebugInfo(
            @PathVariable("fileId") String fileId,
            HttpServletRequest request) {
        
        String userId = getUserIdFromRequest(request);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Unauthorized"));
        }
        
        FileInfo fileInfo = fileService.getFileInfo(fileId);
        if (fileInfo == null) {
            return ResponseEntity.notFound().build();
        }
        
        // Check if user is the owner or file is public
        if (!fileInfo.getUploadedBy().equals(userId) && !fileInfo.isPublic()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Access denied"));
        }
        
        // 创建调试信息
        Map<String, Object> debugInfo = new HashMap<>();
        debugInfo.put("id", fileInfo.getId());
        debugInfo.put("fileName", fileInfo.getFileName());
        debugInfo.put("originalFileName", fileInfo.getOriginalFileName());
        debugInfo.put("path", fileInfo.getPath());
        
        // 获取文件名的字节表示
        if (fileInfo.getFileName() != null) {
            try {
                byte[] filenameBytes = fileInfo.getFileName().getBytes("ISO-8859-1");
                StringBuilder bytesHex = new StringBuilder();
                for (byte b : filenameBytes) {
                    bytesHex.append(String.format("%02X ", b));
                }
                debugInfo.put("fileNameBytesISO", bytesHex.toString());
                
                // 检查是否包含非ASCII字符
                boolean hasNonAscii = false;
                for (byte b : filenameBytes) {
                    if (b < 0) {
                        hasNonAscii = true;
                        break;
                    }
                }
                debugInfo.put("fileNameHasNonAscii", hasNonAscii);
                
                // 尝试用UTF-8解码
                if (hasNonAscii) {
                    String utf8Decoded = new String(filenameBytes, "UTF-8");
                    debugInfo.put("fileNameUTF8Decoded", utf8Decoded);
                    
                    // 获取UTF-8解码后的字节
                    byte[] utf8Bytes = utf8Decoded.getBytes("UTF-8");
                    StringBuilder utf8BytesHex = new StringBuilder();
                    for (byte b : utf8Bytes) {
                        utf8BytesHex.append(String.format("%02X ", b));
                    }
                    debugInfo.put("fileNameUTF8Bytes", utf8BytesHex.toString());
                }
            } catch (Exception e) {
                debugInfo.put("fileNameBytesError", e.getMessage());
            }
        }
        
        if (fileInfo.getOriginalFileName() != null) {
            try {
                byte[] originalFilenameBytes = fileInfo.getOriginalFileName().getBytes("ISO-8859-1");
                StringBuilder bytesHex = new StringBuilder();
                for (byte b : originalFilenameBytes) {
                    bytesHex.append(String.format("%02X ", b));
                }
                debugInfo.put("originalFileNameBytesISO", bytesHex.toString());
                
                // 检查是否包含非ASCII字符
                boolean hasNonAscii = false;
                for (byte b : originalFilenameBytes) {
                    if (b < 0) {
                        hasNonAscii = true;
                        break;
                    }
                }
                debugInfo.put("originalFileNameHasNonAscii", hasNonAscii);
                
                // 尝试用UTF-8解码
                if (hasNonAscii) {
                    String utf8Decoded = new String(originalFilenameBytes, "UTF-8");
                    debugInfo.put("originalFileNameUTF8Decoded", utf8Decoded);
                    
                    // 获取UTF-8解码后的字节
                    byte[] utf8Bytes = utf8Decoded.getBytes("UTF-8");
                    StringBuilder utf8BytesHex = new StringBuilder();
                    for (byte b : utf8Bytes) {
                        utf8BytesHex.append(String.format("%02X ", b));
                    }
                    debugInfo.put("originalFileNameUTF8Bytes", utf8BytesHex.toString());
                }
            } catch (Exception e) {
                debugInfo.put("originalFileNameBytesError", e.getMessage());
            }
        }
        
        // 文件系统中的实际文件名
        try {
            File file = new File(fileInfo.getPath());
            String actualFileName = file.getName();
            debugInfo.put("actualFileName", actualFileName);
            
            if (actualFileName != null) {
                byte[] actualFilenameBytes = actualFileName.getBytes("ISO-8859-1");
                StringBuilder bytesHex = new StringBuilder();
                for (byte b : actualFilenameBytes) {
                    bytesHex.append(String.format("%02X ", b));
                }
                debugInfo.put("actualFileNameBytesISO", bytesHex.toString());
                
                // 检查是否包含非ASCII字符
                boolean hasNonAscii = false;
                for (byte b : actualFilenameBytes) {
                    if (b < 0) {
                        hasNonAscii = true;
                        break;
                    }
                }
                debugInfo.put("actualFileNameHasNonAscii", hasNonAscii);
                
                // 尝试用UTF-8解码
                if (hasNonAscii) {
                    String utf8Decoded = new String(actualFilenameBytes, "UTF-8");
                    debugInfo.put("actualFileNameUTF8Decoded", utf8Decoded);
                }
            }
        } catch (Exception e) {
            debugInfo.put("actualFileNameError", e.getMessage());
        }
        
        return ResponseEntity.ok(ApiResponse.success("File name debug info retrieved", debugInfo));
    }
    
    /**
     * 获取用户文件列表
     */
    @GetMapping("")
    public ResponseEntity<ApiResponse> getUserFiles(
            HttpServletRequest request,
            @RequestParam(value = "t", required = false) Long timestamp) {
        try {
        String userId = getUserIdFromRequest(request);
        if (userId == null) {
                logger.warn("获取文件列表失败: 未授权的请求");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Unauthorized"));
        }
            
            logger.info("获取用户文件列表请求: userId={}, timestamp={}", userId, timestamp);
            
            // 强制重新加载文件信息
            if (timestamp != null) {
                logger.info("强制重新加载文件信息");
                fileService.reloadFileInfo();
            }
        
        List<FileInfo> files = fileService.getUserFiles(userId);
        
            // 检查文件是否实际存在
            List<FileInfo> existingFiles = new ArrayList<>();
            for (FileInfo file : files) {
                File physicalFile = new File(file.getPath());
                boolean exists = physicalFile.exists() && physicalFile.isFile();
                logger.debug("文件 {} 存在: {}", file.getPath(), exists);
                if (exists) {
                    existingFiles.add(file);
                } else {
                    logger.warn("文件不存在: {}", file.getPath());
                }
            }
            
            // 如果有文件不存在，更新文件信息
            if (existingFiles.size() != files.size()) {
                logger.warn("发现 {} 个文件不存在，更新文件信息", files.size() - existingFiles.size());
                fileService.updateFileList(existingFiles);
                files = existingFiles;
            }
            
            logger.info("返回用户文件列表: userId={}, 文件数量={}", userId, files.size());
            return ResponseEntity.ok(ApiResponse.success("Files retrieved successfully", files));
        } catch (Exception e) {
            logger.error("获取文件列表失败: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to get files: " + e.getMessage()));
        }
    }
    
    /**
     * Delete a file
     */
    @DeleteMapping("/{fileId}")
    public ResponseEntity<ApiResponse> deleteFile(
            @PathVariable("fileId") String fileId,
            HttpServletRequest request) {
        
        String userId = getUserIdFromRequest(request);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Unauthorized"));
        }
        
        boolean deleted = fileService.deleteFile(fileId, userId);
        if (!deleted) {
            return ResponseEntity.notFound().build();
        }
        
        return ResponseEntity.ok(ApiResponse.success("File deleted successfully"));
    }
    
    /**
     * Update file info
     */
    @PutMapping("/{fileId}")
    public ResponseEntity<ApiResponse> updateFileInfo(
            @PathVariable("fileId") String fileId,
            @RequestBody FileInfo fileInfo,
            HttpServletRequest request) {
        
        String userId = getUserIdFromRequest(request);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Unauthorized"));
        }
        
        if (!fileId.equals(fileInfo.getId())) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("File ID mismatch"));
        }
        
        FileInfo existingFileInfo = fileService.getFileInfo(fileId);
        if (existingFileInfo == null) {
            return ResponseEntity.notFound().build();
        }
        
        // Check if user is the owner
        if (!existingFileInfo.getUploadedBy().equals(userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Access denied"));
        }
        
        try {
            FileInfo updatedFileInfo = fileService.updateFileInfo(fileInfo);
            return ResponseEntity.ok(ApiResponse.success("File info updated", updatedFileInfo));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }
    
    /**
     * Search files
     */
    @GetMapping("/search")
    public ResponseEntity<ApiResponse> searchFiles(
            @RequestParam("query") String query,
            HttpServletRequest request) {
        
        String userId = getUserIdFromRequest(request);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Unauthorized"));
        }
        
        List<FileInfo> files = fileService.searchFiles(query, userId);
        
        return ResponseEntity.ok(ApiResponse.success("Search results", files));
    }
    
    /**
     * Get public files
     */
    @GetMapping("/public")
    public ResponseEntity<ApiResponse> getPublicFiles() {
        List<FileInfo> files = fileService.getPublicFiles();
        
        return ResponseEntity.ok(ApiResponse.success("Public files retrieved", files));
    }
    
    /**
     * Set file visibility
     */
    @PutMapping("/{fileId}/visibility")
    public ResponseEntity<ApiResponse> setFileVisibility(
            @PathVariable("fileId") String fileId,
            @RequestParam("public") boolean isPublic,
            HttpServletRequest request) {
        
        String userId = getUserIdFromRequest(request);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Unauthorized"));
        }
        
        try {
            FileInfo fileInfo = fileService.setFileVisibility(fileId, isPublic, userId);
            return ResponseEntity.ok(ApiResponse.success("File visibility updated", fileInfo));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }
    
    /**
     * Calculate checksum for a file
     */
    @GetMapping("/{fileId}/checksum")
    public ResponseEntity<ApiResponse> calculateChecksum(
            @PathVariable("fileId") String fileId,
            HttpServletRequest request) {
        
        String userId = getUserIdFromRequest(request);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Unauthorized"));
        }
        
        try {
            FileInfo fileInfo = fileService.getFileInfo(fileId);
            if (fileInfo == null) {
                return ResponseEntity.notFound().build();
            }
            
            // Check if user is the owner or file is public
            if (!fileInfo.getUploadedBy().equals(userId) && !fileInfo.isPublic()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(ApiResponse.error("Access denied"));
            }
            
            String checksum = fileService.calculateChecksum(fileId);
            return ResponseEntity.ok(ApiResponse.success("Checksum calculated", checksum));
        } catch (IOException e) {
            logger.error("Failed to calculate checksum", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to calculate checksum: " + e.getMessage()));
        }
    }
    
    /**
     * 修复文件名编码
     */
    @PostMapping("/{fileId}/fix-filename")
    public ResponseEntity<ApiResponse> fixFilename(
            @PathVariable("fileId") String fileId,
            @RequestParam("filename") String newFilename,
            HttpServletRequest request) {
        
        String userId = getUserIdFromRequest(request);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Unauthorized"));
        }
        
        FileInfo fileInfo = fileService.getFileInfo(fileId);
        if (fileInfo == null) {
            return ResponseEntity.notFound().build();
        }
        
        // Check if user is the owner
        if (!fileInfo.getUploadedBy().equals(userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Access denied"));
        }
        
        // 记录原始和新文件名
        logger.info("修复文件名 - 文件ID: {}, 原始文件名: {}, 新文件名: {}", 
                fileId, fileInfo.getFileName(), newFilename);
        
        // 更新文件名
        fileInfo.setFileName(newFilename);
        fileInfo.setOriginalFileName(newFilename);
        
        // 保存更新后的文件信息
        FileInfo updatedFileInfo = fileService.updateFileInfo(fileInfo);
        
        return ResponseEntity.ok(ApiResponse.success("File name fixed successfully", updatedFileInfo));
    }
    
    /**
     * 批量修复所有文件名编码
     */
    @PostMapping("/fix-all-filenames")
    public ResponseEntity<ApiResponse> fixAllFilenames(
            @RequestParam(value = "encoding", defaultValue = "UTF-8") String encoding,
            HttpServletRequest request) {
        
        String userId = getUserIdFromRequest(request);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Unauthorized"));
        }
        
        List<FileInfo> userFiles = fileService.getUserFiles(userId);
        List<FileInfo> fixedFiles = new ArrayList<>();
        
        for (FileInfo fileInfo : userFiles) {
            try {
                String originalFilename = fileInfo.getFileName();
                // 尝试转换为指定编码
                String fixedFilename = new String(originalFilename.getBytes("ISO-8859-1"), encoding);
                
                // 检查是否包含问号或乱码标记，如果有则跳过
                if (fixedFilename.contains("?") || fixedFilename.contains("")) {
                    logger.warn("文件名转换后仍有乱码，跳过: {}", originalFilename);
                    continue;
                }
                
                // 更新文件名
                fileInfo.setFileName(fixedFilename);
                fileInfo.setOriginalFileName(fixedFilename);
                
                // 保存更新后的文件信息
                FileInfo updatedFileInfo = fileService.updateFileInfo(fileInfo);
                fixedFiles.add(updatedFileInfo);
                
                logger.info("批量修复文件名 - 文件ID: {}, 原始文件名: {}, 新文件名: {}", 
                        fileInfo.getId(), originalFilename, fixedFilename);
            } catch (Exception e) {
                logger.error("修复文件名失败 - 文件ID: " + fileInfo.getId(), e);
            }
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("totalFiles", userFiles.size());
        response.put("fixedFiles", fixedFiles.size());
        response.put("files", fixedFiles);
        
        return ResponseEntity.ok(ApiResponse.success("Fixed " + fixedFiles.size() + " file names", response));
    }
    
    /**
     * 获取支持的文件类型
     */
    @GetMapping("/supported-types")
    public ResponseEntity<ApiResponse> getSupportedFileTypes() {
        List<String> types = fileService.getSupportedFileTypes();
        return ResponseEntity.ok(ApiResponse.success("Supported file types retrieved", types));
    }
    
    /**
     * 系统健康检查端点
     */
    @GetMapping("/health")
    public ResponseEntity<ApiResponse> healthCheck() {
        Map<String, Object> healthInfo = new HashMap<>();
        
        // 检查上传目录
        String uploadDir = fileService.getUploadDirectory();
        File dir = new File(uploadDir);
        boolean uploadDirExists = dir.exists();
        boolean uploadDirWritable = dir.canWrite();
        
        healthInfo.put("uploadDirectory", uploadDir);
        healthInfo.put("uploadDirectoryExists", uploadDirExists);
        healthInfo.put("uploadDirectoryWritable", uploadDirWritable);
        
        // 检查文件信息文件
        boolean fileInfoFileExists = false;
        try {
            String fileInfoFile = uploadDir + File.separator + "fileInfo.json";
            File infoFile = new File(fileInfoFile);
            fileInfoFileExists = infoFile.exists();
            healthInfo.put("fileInfoFileExists", fileInfoFileExists);
        } catch (Exception e) {
            healthInfo.put("fileInfoFileExists", false);
            healthInfo.put("fileInfoFileError", e.getMessage());
        }
        
        // 检查JWT状态
        healthInfo.put("jwtInitialized", jwtUtil != null);
        
        // 检查系统属性
        healthInfo.put("javaVersion", System.getProperty("java.version"));
        healthInfo.put("osName", System.getProperty("os.name"));
        healthInfo.put("userDir", System.getProperty("user.dir"));
        
        boolean healthy = uploadDirExists && uploadDirWritable && fileInfoFileExists;
        
        if (healthy) {
            return ResponseEntity.ok(ApiResponse.success("System is healthy", healthInfo));
        } else {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.error("System has issues", "HEALTH_CHECK_FAILED"));
        }
    }
    
    /**
     * 预览文件
     */
    @GetMapping("/{fileId}/preview")
    public ResponseEntity<ApiResponse> previewFile(
            @PathVariable("fileId") String fileId,
            HttpServletRequest request) {
        
        String userId = getUserIdFromRequest(request);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Unauthorized"));
        }
        
        FileInfo fileInfo = fileService.getFileInfo(fileId);
        if (fileInfo == null) {
            return ResponseEntity.notFound().build();
        }
        
        // Check if user is the owner or file is public
        if (!fileInfo.getUploadedBy().equals(userId) && !fileInfo.isPublic()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Access denied"));
        }
        
        // Check if file is previewable
        if (!fileService.isPreviewable(fileId)) {
            return ResponseEntity.ok(ApiResponse.success("File is not previewable", 
                    Collections.singletonMap("previewable", false)));
        }
        
        try {
            Map<String, Object> previewData = fileService.getPreviewData(fileId);
            previewData.put("previewable", true);
            
            return ResponseEntity.ok(ApiResponse.success("Preview data retrieved", previewData));
        } catch (IOException e) {
            logger.error("Failed to get preview data", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to get preview data: " + e.getMessage()));
        }
    }
    
    /**
     * 直接预览文件（不经过预览数据处理）
     */
    @GetMapping("/{fileId}/direct-preview")
    public ResponseEntity<?> directPreviewFile(
            @PathVariable("fileId") String fileId,
            @RequestParam(value = "token", required = false) String tokenParam,
            HttpServletRequest request) {
        
        // 从请求中获取用户ID，同时支持header和query参数中的token
        String token = tokenParam;
        if (token == null) {
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                token = authHeader.substring(7);
            }
        }
        
        String userId = null;
        if (token != null) {
            try {
                userId = jwtUtil.getUserIdFromToken(token);
            } catch (Exception e) {
                logger.error("Invalid token", e);
            }
        }
        
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Unauthorized"));
        }
        
        try {
            FileInfo fileInfo = fileService.getFileInfo(fileId);
            if (fileInfo == null) {
                return ResponseEntity.notFound().build();
            }
            
            // Check if user is the owner or file is public
            if (!fileInfo.getUploadedBy().equals(userId) && !fileInfo.isPublic()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(ApiResponse.error("Access denied"));
            }
            
            // 获取文件
            File file = fileService.downloadFile(fileId);
            InputStream inputStream = new FileInputStream(file);
            
            // 设置响应头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(fileInfo.getContentType()));
            headers.setContentDisposition(ContentDisposition.inline().filename(fileInfo.getFileName()).build());
            headers.setContentLength(file.length());
            
            // 添加CORS头，允许在iframe中加载
            headers.add("Access-Control-Allow-Origin", "*");
            headers.add("Access-Control-Allow-Methods", "GET, OPTIONS");
            headers.add("Access-Control-Allow-Headers", "Content-Type");
            headers.add("X-Frame-Options", "ALLOWALL");
            
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(new InputStreamResource(inputStream));
        } catch (IOException e) {
            logger.error("Failed to preview file", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to preview file: " + e.getMessage()));
        }
    }
    
    /**
     * 直接访问文件（无需下载，用于在浏览器中查看）
     */
    @GetMapping("/view/{fileId}")
    public ResponseEntity<?> viewFile(
            @PathVariable("fileId") String fileId,
            @RequestParam(value = "token", required = false) String tokenParam,
            HttpServletRequest request) {
        
        logger.info("收到文件直接访问请求: fileId={}", fileId);
        
        try {
            // 从请求中获取用户ID，同时支持header和query参数中的token
            String token = tokenParam;
            if (token == null) {
                String authHeader = request.getHeader("Authorization");
                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                    token = authHeader.substring(7);
                }
            }
            
            String userId = null;
            if (token != null) {
                try {
                    userId = jwtUtil.getUserIdFromToken(token);
                } catch (Exception e) {
                    logger.error("无效的token", e);
                }
            }
            
            if (userId == null) {
                logger.warn("文件访问失败: 未授权的请求");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error("Unauthorized"));
            }
            
            FileInfo fileInfo = fileService.getFileInfo(fileId);
            if (fileInfo == null) {
                logger.warn("文件访问失败: 文件不存在, fileId={}", fileId);
                return ResponseEntity.notFound().build();
            }
            
            // Check if user is the owner or file is public
            if (!fileInfo.getUploadedBy().equals(userId) && !fileInfo.isPublic()) {
                logger.warn("文件访问失败: 权限不足, fileId={}, 请求用户={}, 文件所有者={}", 
                        fileId, userId, fileInfo.getUploadedBy());
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(ApiResponse.error("Access denied"));
            }
            
            File file = new File(fileInfo.getPath());
            if (!file.exists()) {
                logger.warn("文件访问失败: 文件不存在于磁盘, 路径={}", fileInfo.getPath());
                return ResponseEntity.notFound().build();
            }
            
            InputStream inputStream = new FileInputStream(file);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(fileInfo.getContentType()));
            headers.setContentDisposition(ContentDisposition.inline().filename(fileInfo.getFileName()).build());
            headers.setContentLength(file.length());
            
            // 添加CORS头，允许在iframe中加载
            headers.add("Access-Control-Allow-Origin", "*");
            headers.add("Access-Control-Allow-Methods", "GET, OPTIONS");
            headers.add("Access-Control-Allow-Headers", "Content-Type");
            headers.add("X-Frame-Options", "ALLOWALL");
            
            logger.info("文件访问成功: fileId={}, 文件名={}", fileId, fileInfo.getFileName());
            
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(new InputStreamResource(inputStream));
        } catch (IOException e) {
            logger.error("文件访问失败: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to access file: " + e.getMessage()));
        }
    }
    
    /**
     * 预览文本文件内容
     */
    @GetMapping("/{fileId}/text-preview")
    public ResponseEntity<?> textPreviewFile(
            @PathVariable("fileId") String fileId,
            @RequestParam(value = "token", required = false) String tokenParam,
            HttpServletRequest request) {
        
        logger.info("接收到文本预览请求 fileId: {}", fileId);
        
        // 从请求中获取用户ID，同时支持header和query参数中的token
        String token = tokenParam;
        if (token == null) {
            String authHeader = request.getHeader("Authorization");
            logger.info("Authorization头: {}", authHeader);
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                token = authHeader.substring(7);
            }
        } else {
            logger.info("从URL参数获取到token");
        }
        
        logger.info("解析token: {}", token != null ? token.substring(0, Math.min(10, token.length())) + "..." : "null");
        
        String userId = null;
        if (token != null) {
            try {
                userId = jwtUtil.getUserIdFromToken(token);
                logger.info("从token获取到用户ID: {}", userId);
            } catch (Exception e) {
                logger.error("无效的token: {}", e.getMessage());
            }
        } else {
            logger.warn("没有提供token");
        }
        
        if (userId == null) {
            logger.warn("未授权访问，无法获取有效的用户ID");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Unauthorized"));
        }
        
        try {
            FileInfo fileInfo = fileService.getFileInfo(fileId);
            if (fileInfo == null) {
                logger.warn("找不到文件: {}", fileId);
                return ResponseEntity.notFound().build();
            }
            
            // Check if user is the owner or file is public
            logger.info("文件所有者: {}, 当前用户: {}, 文件是否公开: {}", 
                    fileInfo.getUploadedBy(), userId, fileInfo.isPublic());
            if (!fileInfo.getUploadedBy().equals(userId) && !fileInfo.isPublic()) {
                logger.warn("访问被拒绝，用户 {} 尝试访问用户 {} 的文件", userId, fileInfo.getUploadedBy());
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(ApiResponse.error("Access denied"));
            }
            
            // 获取文件
            File file = fileService.downloadFile(fileId);
            logger.info("成功获取文件: {}, 大小: {} 字节", fileInfo.getFileName(), file.length());
            
            // 读取文件内容
            String content;
            String contentType = fileInfo.getContentType();
            
            // 如果是文本文件，直接读取内容
            if (contentType != null && (
                    contentType.startsWith("text/") || 
                    contentType.equals("application/json") ||
                    contentType.equals("application/xml") ||
                    contentType.equals("application/javascript") ||
                    contentType.endsWith("+xml") ||
                    contentType.endsWith("+json"))) {
                
                // 读取文件内容，最多读取前1MB
                byte[] bytes = new byte[Math.min((int)file.length(), 1024 * 1024)];
                try (FileInputStream fis = new FileInputStream(file)) {
                    fis.read(bytes);
                }
                content = new String(bytes, StandardCharsets.UTF_8);
                logger.info("成功读取文本文件内容，长度: {} 字符", content.length());
                
                // 如果文件大于1MB，添加提示
                if (file.length() > 1024 * 1024) {
                    content += "\n\n... (文件过大，仅显示前1MB内容) ...";
                }
            } else {
                // 非文本文件，返回错误
                logger.warn("不支持的文件类型: {}", contentType);
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("不支持预览此类型的文件: " + contentType));
            }
            
            // 创建响应对象
            Map<String, Object> response = new HashMap<>();
            response.put("fileName", fileInfo.getFileName());
            response.put("contentType", fileInfo.getContentType());
            response.put("size", fileInfo.getSize());
            response.put("content", content);
            
            logger.info("成功返回文件预览响应");
            return ResponseEntity.ok(ApiResponse.success("文件内容预览", response));
        } catch (IOException e) {
            logger.error("预览文件失败: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to preview file: " + e.getMessage()));
        }
    }
    
    /**
     * 重新加载配置
     */
    @GetMapping("/reload-config")
    public ResponseEntity<ApiResponse> reloadConfig(HttpServletRequest request) {
        String userId = getUserIdFromRequest(request);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Unauthorized"));
        }
        
        // 重新初始化FileService
        try {
            // 反射获取init方法并调用
            java.lang.reflect.Method initMethod = fileService.getClass().getMethod("init");
            initMethod.invoke(fileService);
            
            // 获取支持的文件类型
            List<String> types = fileService.getSupportedFileTypes();
            
            Map<String, Object> response = new HashMap<>();
            response.put("supportedTypes", types);
            response.put("reloaded", true);
            
            return ResponseEntity.ok(ApiResponse.success("Configuration reloaded", response));
        } catch (Exception e) {
            logger.error("Failed to reload configuration", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to reload configuration: " + e.getMessage()));
        }
    }
    
    /**
     * 测试上传目录
     */
    @GetMapping("/test-upload-dir")
    public ResponseEntity<ApiResponse> testUploadDirectory() {
        String uploadDir = fileService.getUploadDirectory();
        File dir = new File(uploadDir);
        
        Map<String, Object> result = new HashMap<>();
        result.put("uploadDirectory", uploadDir);
        result.put("exists", dir.exists());
        result.put("canWrite", dir.canWrite());
        result.put("isDirectory", dir.isDirectory());
        result.put("absolutePath", dir.getAbsolutePath());
        
        // 系统属性
        result.put("user.dir", System.getProperty("user.dir"));
        result.put("catalina.base", System.getProperty("catalina.base"));
        result.put("user.home", System.getProperty("user.home"));
        
        // 尝试创建目录
        if (!dir.exists()) {
            boolean created = dir.mkdirs();
            result.put("directoryCreated", created);
        }
        
        // 尝试创建测试文件
        try {
            File testFile = new File(dir, "test.txt");
            boolean created = testFile.createNewFile();
            result.put("testFileCreated", created);
            if (created) {
                try (FileWriter writer = new FileWriter(testFile)) {
                    writer.write("Test file created at " + new Date());
                }
                testFile.delete(); // 清理测试文件
            }
        } catch (IOException e) {
            result.put("testFileError", e.getMessage());
        }
        
        return ResponseEntity.ok(ApiResponse.success("上传目录测试结果", result));
    }
    
    /**
     * 重置文件信息（用于修复损坏的fileInfo.json）
     */
    @GetMapping("/reset-file-info")
    public ResponseEntity<ApiResponse> resetFileInfo(HttpServletRequest request) {
        try {
            String userId = getUserIdFromRequest(request);
            if (userId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error("Unauthorized"));
            }
            
            logger.info("收到重置文件信息请求: userId={}", userId);
            
            // 清空文件信息并重新扫描上传目录
            boolean success = fileService.resetFileInfo();
            
            if (success) {
                return ResponseEntity.ok(ApiResponse.success("File information reset successfully"));
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(ApiResponse.error("Failed to reset file information"));
            }
        } catch (Exception e) {
            logger.error("重置文件信息失败: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to reset file information: " + e.getMessage()));
        }
    }
    
    /**
     * Helper method to extract user ID from request
     */
    private String getUserIdFromRequest(HttpServletRequest request) {
        // 首先检查Authorization头
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            return jwtUtil.getUserIdFromToken(token);
        }
        
        // 如果没有Authorization头，检查查询参数中的token
        String tokenParam = request.getParameter("token");
        if (tokenParam != null && !tokenParam.isEmpty()) {
            logger.debug("Found token in query parameter");
            return jwtUtil.getUserIdFromToken(tokenParam);
        }
        
        return null;
    }
    
    /**
     * Inner class for chunk upload response
     */
    private static class ChunkResponse {
        private String fileId;
        private int chunkNumber;
        private int totalChunks;
        
        public ChunkResponse(String fileId, int chunkNumber, int totalChunks) {
            this.fileId = fileId;
            this.chunkNumber = chunkNumber;
            this.totalChunks = totalChunks;
        }
        
        public String getFileId() {
            return fileId;
        }
        
        public int getChunkNumber() {
            return chunkNumber;
        }
        
        public int getTotalChunks() {
            return totalChunks;
        }
    }
} 