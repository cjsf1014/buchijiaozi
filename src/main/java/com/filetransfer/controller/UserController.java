package com.filetransfer.controller;

import com.filetransfer.model.ApiResponse;
import com.filetransfer.model.User;
import com.filetransfer.service.UserService;
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
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * REST Controller for user operations
 */
@RestController
@RequestMapping("/api/users")
public class UserController {
    
    private static final Logger logger = LoggerFactory.getLogger(UserController.class);
    
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
            
            logger.info("JwtUtil initialized in UserController");
        } catch (Exception e) {
            logger.warn("Failed to load application.properties in UserController, using default values", e);
            // 使用默认值
            jwtUtil.setSecret("defaultSecretKeyThatIsLongEnoughForHmacSHA256Algorithm");
            jwtUtil.setExpiration(86400000L); // 24小时
            jwtUtil.init();
        }
    }
    
    /**
     * Test endpoint to verify controller routing
     */
    @GetMapping("/test")
    public ResponseEntity<String> testEndpoint() {
        logger.info("Test endpoint reached");
        return ResponseEntity.ok("UserController is working!");
    }
    
    /**
     * Simple health check endpoint that doesn't require request body parsing
     */
    @GetMapping("/health")
    public String healthCheck() {
        logger.info("Health check endpoint reached");
        return "{\"status\":\"UP\"}";
    }
    
    /**
     * Simple test endpoint
     */
    @GetMapping("/simple-test")
    public ResponseEntity<String> simpleTest() {
        logger.info("Simple test endpoint in UserController reached");
        return ResponseEntity.ok("UserController simple test is working!");
    }
    
    /**
     * Register a new user
     */
    @PostMapping("/register")
    public ResponseEntity<ApiResponse> registerUser(@RequestBody User user) {
        try {
            logger.info("Received registration request for username: {}", user.getUsername());
            
            if (user.getUsername() == null || user.getEmail() == null || user.getPassword() == null) {
                logger.warn("Registration failed: Missing required fields");
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("Username, email and password are required"));
            }
            
            User registeredUser = userService.registerUser(user);
            
            // 创建一个新的用户对象，不包含密码
            User safeUser = new User();
            safeUser.setId(registeredUser.getId());
            safeUser.setUsername(registeredUser.getUsername());
            safeUser.setEmail(registeredUser.getEmail());
            safeUser.setFullName(registeredUser.getFullName());
            safeUser.setCreatedDate(registeredUser.getCreatedDate());
            safeUser.setRole(registeredUser.getRole());
            safeUser.setEnabled(registeredUser.isEnabled());
            safeUser.setTotalStorageUsed(registeredUser.getTotalStorageUsed());
            safeUser.setStorageLimit(registeredUser.getStorageLimit());
            
            logger.info("User registered successfully: {}", registeredUser.getUsername());
            return ResponseEntity.ok(ApiResponse.success("User registered successfully", safeUser));
        } catch (IllegalArgumentException e) {
            logger.error("Registration failed: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            logger.error("Unexpected error during registration", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("An unexpected error occurred"));
        }
    }
    
    /**
     * Login
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse> login(@RequestParam String username, @RequestParam String password) {
        logger.info("Login attempt for username: {}", username);
        
        User user = userService.authenticateUser(username, password);
        
        if (user == null) {
            logger.warn("Login failed for username: {}", username);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Invalid username or password"));
        }
        
        // Update last login date
        user.setLastLoginDate(new Date());
        userService.updateUser(user);
        
        // Generate JWT token
        String token = jwtUtil.generateToken(user.getId());
        
        Map<String, Object> response = new HashMap<>();
        response.put("token", token);
        
        // 创建一个新的用户对象，不包含密码
        User safeUser = new User();
        safeUser.setId(user.getId());
        safeUser.setUsername(user.getUsername());
        safeUser.setEmail(user.getEmail());
        safeUser.setFullName(user.getFullName());
        safeUser.setCreatedDate(user.getCreatedDate());
        safeUser.setLastLoginDate(user.getLastLoginDate());
        safeUser.setRole(user.getRole());
        safeUser.setEnabled(user.isEnabled());
        safeUser.setTotalStorageUsed(user.getTotalStorageUsed());
        safeUser.setStorageLimit(user.getStorageLimit());
        
        response.put("user", safeUser);
        
        logger.info("Login successful for username: {}", username);
        return ResponseEntity.ok(ApiResponse.success("Login successful", response));
    }
    
    /**
     * Get current user profile
     */
    @GetMapping("/profile")
    public ResponseEntity<ApiResponse> getUserProfile(HttpServletRequest request) {
        String userId = getUserIdFromRequest(request);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Unauthorized"));
        }
        
        User user = userService.getUserById(userId);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }
        
        // 创建一个新的用户对象，不包含密码
        User safeUser = new User();
        safeUser.setId(user.getId());
        safeUser.setUsername(user.getUsername());
        safeUser.setEmail(user.getEmail());
        safeUser.setFullName(user.getFullName());
        safeUser.setCreatedDate(user.getCreatedDate());
        safeUser.setLastLoginDate(user.getLastLoginDate());
        safeUser.setRole(user.getRole());
        safeUser.setEnabled(user.isEnabled());
        safeUser.setTotalStorageUsed(user.getTotalStorageUsed());
        safeUser.setStorageLimit(user.getStorageLimit());
        
        return ResponseEntity.ok(ApiResponse.success("User profile retrieved", safeUser));
    }
    
    /**
     * Update user profile
     */
    @PutMapping("/profile")
    public ResponseEntity<ApiResponse> updateUserProfile(
            @RequestBody User user,
            HttpServletRequest request) {
        
        String userId = getUserIdFromRequest(request);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Unauthorized"));
        }
        
        if (!userId.equals(user.getId())) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("User ID mismatch"));
        }
        
        try {
            User updatedUser = userService.updateUser(user);
            
            // Remove password from response
            updatedUser.setPassword(null);
            
            return ResponseEntity.ok(ApiResponse.success("User profile updated", updatedUser));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }
    
    /**
     * Change password
     */
    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse> changePassword(
            @RequestParam String currentPassword,
            @RequestParam String newPassword,
            HttpServletRequest request) {
        
        String userId = getUserIdFromRequest(request);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Unauthorized"));
        }
        
        User user = userService.getUserById(userId);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }
        
        // Verify current password
        if (!currentPassword.equals(user.getPassword())) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Current password is incorrect"));
        }
        
        // Update password
        user.setPassword(newPassword);
        userService.updateUser(user);
        
        return ResponseEntity.ok(ApiResponse.success("Password changed successfully"));
    }
    
    /**
     * Get storage usage
     */
    @GetMapping("/storage")
    public ResponseEntity<ApiResponse> getStorageUsage(HttpServletRequest request) {
        String userId = getUserIdFromRequest(request);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Unauthorized"));
        }
        
        User user = userService.getUserById(userId);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }
        
        Map<String, Object> storageInfo = new HashMap<>();
        storageInfo.put("used", user.getTotalStorageUsed());
        // 设置无限制的存储空间
        storageInfo.put("limit", -1L);
        storageInfo.put("usedFormatted", user.getFormattedStorageUsed());
        storageInfo.put("limitFormatted", "无限制");
        // 由于是无限制，所以使用率显示为接近0
        storageInfo.put("usagePercentage", 0.1);
        
        return ResponseEntity.ok(ApiResponse.success("Storage usage retrieved", storageInfo));
    }
    
    /**
     * Admin: Get all users
     */
    @GetMapping("/admin/all")
    public ResponseEntity<ApiResponse> getAllUsers(HttpServletRequest request) {
        String userId = getUserIdFromRequest(request);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Unauthorized"));
        }
        
        User user = userService.getUserById(userId);
        if (user == null || !"ADMIN".equals(user.getRole())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Access denied"));
        }
        
        return ResponseEntity.ok(ApiResponse.success("Users retrieved", userService.getAllUsers()));
    }
    
    /**
     * Admin: Update user storage limit
     */
    @PutMapping("/admin/storage-limit/{userId}")
    public ResponseEntity<ApiResponse> updateStorageLimit(
            @PathVariable("userId") String targetUserId,
            @RequestParam long storageLimit,
            HttpServletRequest request) {
        
        String userId = getUserIdFromRequest(request);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Unauthorized"));
        }
        
        User user = userService.getUserById(userId);
        if (user == null || !"ADMIN".equals(user.getRole())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Access denied"));
        }
        
        try {
            User updatedUser = userService.updateStorageLimit(targetUserId, storageLimit);
            return ResponseEntity.ok(ApiResponse.success("Storage limit updated", updatedUser));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }
    
    /**
     * Validate token
     */
    @GetMapping("/validate-token")
    public ResponseEntity<ApiResponse> validateToken(
            @RequestParam(value = "token", required = false) String tokenParam,
            HttpServletRequest request) {
        
        logger.info("Token验证请求");
        
        // 从请求中获取token
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
        
        if (token == null) {
            logger.warn("没有提供token");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("No token provided"));
        }
        
        logger.info("解析token: {}", token.substring(0, Math.min(10, token.length())) + "...");
        
        // 验证token
        if (!jwtUtil.validateToken(token)) {
            logger.warn("无效的token");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Invalid token"));
        }
        
        // 获取用户ID
        String userId = jwtUtil.getUserIdFromToken(token);
        if (userId == null) {
            logger.warn("无法从token获取用户ID");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Invalid token"));
        }
        
        logger.info("Token有效，用户ID: {}", userId);
        
        // 获取用户信息
        User user = userService.getUserById(userId);
        if (user == null) {
            logger.warn("找不到用户: {}", userId);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("User not found"));
        }
        
        // 创建响应
        Map<String, Object> response = new HashMap<>();
        response.put("userId", userId);
        response.put("username", user.getUsername());
        response.put("valid", true);
        
        return ResponseEntity.ok(ApiResponse.success("Token is valid", response));
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
} 