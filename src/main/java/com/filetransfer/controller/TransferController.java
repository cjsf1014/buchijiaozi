package com.filetransfer.controller;

import com.filetransfer.model.ApiResponse;
import com.filetransfer.model.FileInfo;
import com.filetransfer.model.TransferRequest;
import com.filetransfer.service.FileService;
import com.filetransfer.service.TransferService;
import com.filetransfer.util.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Properties;

/**
 * REST Controller for file transfer operations
 */
@RestController
@RequestMapping("/api/transfers")
public class TransferController {
    
    private static final Logger logger = LoggerFactory.getLogger(TransferController.class);
    
    @Autowired
    private TransferService transferService;
    
    @Autowired
    private FileService fileService;
    
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
            
            logger.info("JwtUtil initialized in TransferController");
        } catch (Exception e) {
            logger.warn("Failed to load application.properties in TransferController, using default values", e);
            // 使用默认值
            jwtUtil.setSecret("defaultSecretKeyThatIsLongEnoughForHmacSHA256Algorithm");
            jwtUtil.setExpiration(86400000L); // 24小时
            jwtUtil.init();
        }
    }
    
    /**
     * Create a new transfer request
     */
    @PostMapping
    public ResponseEntity<ApiResponse> createTransferRequest(
            @RequestBody TransferRequest transferRequest,
            HttpServletRequest request) {
        
        String userId = getUserIdFromRequest(request);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Unauthorized"));
        }
        
        try {
            // Set sender ID
            transferRequest.setSenderId(userId);
            
            // Validate file ownership
            FileInfo fileInfo = fileService.getFileInfo(transferRequest.getFileId());
            if (fileInfo == null) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("File not found"));
            }
            
            if (!fileInfo.getUploadedBy().equals(userId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(ApiResponse.error("You don't own this file"));
            }
            
            TransferRequest createdRequest = transferService.createTransferRequest(transferRequest);
            
            return ResponseEntity.ok(ApiResponse.success("Transfer request created", createdRequest));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }
    
    /**
     * Get transfer requests sent by the current user
     */
    @GetMapping("/sent")
    public ResponseEntity<ApiResponse> getSentTransferRequests(HttpServletRequest request) {
        String userId = getUserIdFromRequest(request);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Unauthorized"));
        }
        
        List<TransferRequest> transferRequests = transferService.getSentTransferRequests(userId);
        
        return ResponseEntity.ok(ApiResponse.success("Transfer requests retrieved", transferRequests));
    }
    
    /**
     * Get transfer requests received by the current user
     */
    @GetMapping("/received")
    public ResponseEntity<ApiResponse> getReceivedTransferRequests(HttpServletRequest request) {
        String userId = getUserIdFromRequest(request);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Unauthorized"));
        }
        
        List<TransferRequest> transferRequests = transferService.getReceivedTransferRequests(userId);
        
        return ResponseEntity.ok(ApiResponse.success("Transfer requests retrieved", transferRequests));
    }
    
    /**
     * Get a specific transfer request
     */
    @GetMapping("/{transferId}")
    public ResponseEntity<ApiResponse> getTransferRequest(
            @PathVariable("transferId") String transferId,
            HttpServletRequest request) {
        
        String userId = getUserIdFromRequest(request);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Unauthorized"));
        }
        
        TransferRequest transferRequest = transferService.getTransferRequest(transferId);
        if (transferRequest == null) {
            return ResponseEntity.notFound().build();
        }
        
        // Check if user is the sender or recipient
        if (!transferRequest.getSenderId().equals(userId) && 
            (transferRequest.getRecipientId() == null || !transferRequest.getRecipientId().equals(userId))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Access denied"));
        }
        
        return ResponseEntity.ok(ApiResponse.success("Transfer request retrieved", transferRequest));
    }
    
    /**
     * Cancel a transfer request
     */
    @DeleteMapping("/{transferId}")
    public ResponseEntity<ApiResponse> cancelTransferRequest(
            @PathVariable("transferId") String transferId,
            HttpServletRequest request) {
        
        String userId = getUserIdFromRequest(request);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Unauthorized"));
        }
        
        try {
            boolean cancelled = transferService.cancelTransferRequest(transferId, userId);
            if (!cancelled) {
                return ResponseEntity.notFound().build();
            }
            
            return ResponseEntity.ok(ApiResponse.success("Transfer request cancelled"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }
    
    /**
     * Access a transfer by access code (no authentication required)
     */
    @GetMapping("/access/{transferId}")
    public ResponseEntity<ApiResponse> accessTransfer(
            @PathVariable("transferId") String transferId,
            @RequestParam("code") String accessCode) {
        
        try {
            TransferRequest transferRequest = transferService.accessTransferRequest(transferId, accessCode);
            
            if (transferRequest == null) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(ApiResponse.error("Invalid access code or transfer ID"));
            }
            
            if (!transferRequest.isActive()) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("Transfer request is expired or inactive"));
            }
            
            // Get file info
            FileInfo fileInfo = fileService.getFileInfo(transferRequest.getFileId());
            if (fileInfo == null) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("File not found"));
            }
            
            // Return transfer info with file details
            return ResponseEntity.ok(ApiResponse.success("Transfer access granted", 
                    new TransferAccessResponse(transferRequest, fileInfo)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
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
     * Inner class for transfer access response
     */
    private static class TransferAccessResponse {
        private TransferRequest transferRequest;
        private FileInfo fileInfo;
        
        public TransferAccessResponse(TransferRequest transferRequest, FileInfo fileInfo) {
            this.transferRequest = transferRequest;
            this.fileInfo = fileInfo;
        }
        
        public TransferRequest getTransferRequest() {
            return transferRequest;
        }
        
        public FileInfo getFileInfo() {
            return fileInfo;
        }
    }
} 