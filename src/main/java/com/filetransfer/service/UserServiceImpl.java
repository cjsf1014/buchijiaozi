package com.filetransfer.service;

import com.filetransfer.model.User;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Implementation of the UserService interface
 */
@Service
public class UserServiceImpl implements UserService {
    
    private static final Logger logger = LoggerFactory.getLogger(UserServiceImpl.class);
    
    @Value("${file.upload.directory}")
    private String baseDirectory;
    
    private String userDataFile;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // In-memory cache for user information
    private final Map<String, User> userMap = new ConcurrentHashMap<>();
    private final Map<String, User> usernameMap = new ConcurrentHashMap<>();
    
    @PostConstruct
    public void init() {
        // 确保基础目录存在
        File baseDir = new File(baseDirectory);
        if (!baseDir.exists()) {
            baseDir.mkdirs();
        }
        
        // 设置用户数据文件路径
        userDataFile = baseDirectory + File.separator + "users.json";
        
        // 加载用户数据
        loadUsers();
    }
    
    /**
     * 从文件加载用户数据
     */
    private void loadUsers() {
        File file = new File(userDataFile);
        if (!file.exists()) {
            logger.info("User data file does not exist, will be created when users are registered");
            return;
        }
        
        try {
            // 创建一个专门用于加载用户数据的ObjectMapper，确保密码字段被保留
            ObjectMapper userMapper = new ObjectMapper();
            // 配置ObjectMapper忽略未知属性
            userMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            // 不忽略任何字段
            userMapper.setSerializationInclusion(JsonInclude.Include.ALWAYS);
            
            // 尝试加载用户数据
            List<User> users = userMapper.readValue(file, new TypeReference<List<User>>() {});
            
            // 清空当前缓存
            userMap.clear();
            usernameMap.clear();
            
            // 将用户添加到缓存
            for (User user : users) {
                if (!user.hasPassword()) {
                    logger.warn("User {} has no password in loaded data", user.getUsername());
                } else {
                    logger.debug("Loaded user: id={}, username={}, password length={}", 
                            user.getId(), user.getUsername(), 
                            user.getPassword() != null ? user.getPassword().length() : 0);
                }
                
                userMap.put(user.getId(), user);
                usernameMap.put(user.getUsername().toLowerCase(), user);
            }
            
            logger.info("Loaded {} users from file", users.size());
        } catch (IOException e) {
            logger.error("Failed to load user data", e);
            
            // 如果是未知属性错误，尝试备份并重置用户数据文件
            if (e.getMessage() != null && e.getMessage().contains("Unrecognized field")) {
                try {
                    // 创建备份
                    String backupFile = userDataFile + ".bak." + System.currentTimeMillis();
                    Files.copy(Paths.get(userDataFile), Paths.get(backupFile));
                    logger.info("Created backup of corrupted user data file: {}", backupFile);
                    
                    // 创建一个空的用户列表文件
                    new ObjectMapper().writeValue(new File(userDataFile), new ArrayList<User>());
                    logger.info("Reset user data file with empty user list");
                } catch (IOException backupError) {
                    logger.error("Failed to backup/reset corrupted user data file", backupError);
                }
            }
        }
    }
    
    /**
     * 将用户数据保存到文件
     */
    private void saveUsers() {
        try {
            List<User> users = new ArrayList<>(userMap.values());
            
            // 检查用户数据，确保密码不为空
            for (User user : users) {
                if (!user.hasPassword()) {
                    logger.warn("User {} has no password before saving to file", user.getUsername());
                } else {
                    logger.debug("User {} has password of length {} before saving to file", 
                            user.getUsername(), user.getPassword().length());
                }
            }
            
            logger.debug("Saving {} users to file, current objectMapper config: {}", 
                    users.size(), objectMapper.getSerializationConfig());
            
            // 创建一个专门用于保存用户数据的ObjectMapper，确保密码字段被保留
            ObjectMapper userMapper = new ObjectMapper();
            // 不忽略任何字段
            userMapper.setSerializationInclusion(JsonInclude.Include.ALWAYS);
            // 禁用所有可能导致字段被忽略的功能
            userMapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            userMapper.disable(com.fasterxml.jackson.databind.MapperFeature.USE_ANNOTATIONS);
            // 美化输出，方便调试
            userMapper.enable(SerializationFeature.INDENT_OUTPUT);
            
            // 先写入临时文件
            File tempFile = new File(userDataFile + ".tmp");
            String jsonContent = userMapper.writeValueAsString(users);
            
            // 检查JSON中是否包含密码字段
            for (User user : users) {
                if (user.hasPassword() && !jsonContent.contains("\"password\":\"" + user.getPassword() + "\"")) {
                    logger.error("Password for user {} is missing in JSON output!", user.getUsername());
                    // 尝试手动构建JSON
                    StringBuilder sb = new StringBuilder();
                    sb.append("[\n");
                    for (int i = 0; i < users.size(); i++) {
                        User u = users.get(i);
                        sb.append("  {\n");
                        sb.append("    \"id\": \"").append(u.getId()).append("\",\n");
                        sb.append("    \"username\": \"").append(u.getUsername()).append("\",\n");
                        sb.append("    \"password\": \"").append(u.getPassword()).append("\",\n");
                        sb.append("    \"email\": \"").append(u.getEmail()).append("\",\n");
                        if (u.getFullName() != null) {
                            sb.append("    \"fullName\": \"").append(u.getFullName()).append("\",\n");
                        }
                        sb.append("    \"createdDate\": ").append(u.getCreatedDate().getTime()).append(",\n");
                        if (u.getLastLoginDate() != null) {
                            sb.append("    \"lastLoginDate\": ").append(u.getLastLoginDate().getTime()).append(",\n");
                        }
                        sb.append("    \"enabled\": ").append(u.isEnabled()).append(",\n");
                        sb.append("    \"role\": \"").append(u.getRole()).append("\",\n");
                        sb.append("    \"totalStorageUsed\": ").append(u.getTotalStorageUsed()).append(",\n");
                        sb.append("    \"storageLimit\": ").append(u.getStorageLimit()).append("\n");
                        sb.append("  }");
                        if (i < users.size() - 1) {
                            sb.append(",");
                        }
                        sb.append("\n");
                    }
                    sb.append("]\n");
                    jsonContent = sb.toString();
                    logger.info("Using manually constructed JSON to ensure passwords are included");
                    break;
                }
            }
            
            // 写入文件
            Files.write(tempFile.toPath(), jsonContent.getBytes(StandardCharsets.UTF_8));
            
            // 检查临时文件是否写入成功
            if (tempFile.exists() && tempFile.length() > 0) {
                // 从临时文件读取用户列表，检查密码是否正确
                List<User> tempUsers = userMapper.readValue(tempFile, new TypeReference<List<User>>() {});
                boolean passwordsOk = true;
                for (User user : tempUsers) {
                    if (!user.hasPassword()) {
                        logger.warn("User {} has no password in temporary file", user.getUsername());
                        passwordsOk = false;
                    } else {
                        logger.debug("User {} has password of length {} in temporary file", 
                                user.getUsername(), user.getPassword().length());
                    }
                }
                
                // 如果密码验证通过，移动临时文件到最终位置
                if (passwordsOk) {
                    Files.move(tempFile.toPath(), new File(userDataFile).toPath(), 
                            StandardCopyOption.REPLACE_EXISTING);
            logger.info("Saved {} users to file", users.size());
                } else {
                    logger.error("Password validation failed, not updating user data file");
                    // 不删除临时文件，以便于调试
                    logger.info("Temporary file kept for debugging: {}", tempFile.getAbsolutePath());
                }
            } else {
                logger.error("Failed to write user data to temporary file");
            }
            
            // 验证保存后的数据
            try {
                List<User> savedUsers = userMapper.readValue(new File(userDataFile), new TypeReference<List<User>>() {});
                for (User user : savedUsers) {
                    if (!user.hasPassword()) {
                        logger.warn("User {} has no password after saving to file", user.getUsername());
                    } else {
                        logger.debug("User {} has password of length {} after saving to file", 
                                user.getUsername(), user.getPassword().length());
                    }
                }
            } catch (IOException e) {
                logger.error("Failed to verify saved user data", e);
            }
        } catch (IOException e) {
            logger.error("Failed to save user data", e);
        }
    }
    
    @Override
    public User registerUser(User user) {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }
        
        if (user.getUsername() == null || user.getUsername().isEmpty()) {
            throw new IllegalArgumentException("Username cannot be empty");
        }
        
        if (user.getPassword() == null || user.getPassword().isEmpty()) {
            throw new IllegalArgumentException("Password cannot be empty");
        }
        
        logger.debug("Registering user: username={}, email={}, password length={}", 
                user.getUsername(), user.getEmail(), 
                user.getPassword() != null ? user.getPassword().length() : 0);
        
        // Check if username already exists
        if (usernameMap.containsKey(user.getUsername().toLowerCase())) {
            throw new IllegalArgumentException("Username already exists");
        }
        
        // Generate ID if not provided
        if (user.getId() == null) {
            user.setId(UUID.randomUUID().toString());
        }
        
        // 确保密码被正确设置
        String password = user.getPassword();
        if (password == null || password.isEmpty()) {
            logger.warn("Password is empty during registration for user: {}", user.getUsername());
            throw new IllegalArgumentException("Password cannot be empty");
        }
        
        // 创建一个新的用户对象，确保所有字段都被正确设置
        User newUser = new User();
        newUser.setId(user.getId());
        newUser.setUsername(user.getUsername());
        newUser.setEmail(user.getEmail());
        newUser.setPassword(password); // 显式设置密码
        newUser.setFullName(user.getFullName());
        newUser.setCreatedDate(new Date());
        newUser.setEnabled(true);
        newUser.setRole("USER");
        newUser.setStorageLimit(1073741824); // 1GB default storage limit
        newUser.setTotalStorageUsed(0);
        
        logger.info("Created new user with password: {}", password);
        
        // Store user in memory cache
        userMap.put(newUser.getId(), newUser);
        usernameMap.put(newUser.getUsername().toLowerCase(), newUser);
        
        // 验证密码是否被正确设置
        User storedUser = userMap.get(newUser.getId());
        if (storedUser == null || !storedUser.hasPassword()) {
            logger.error("Password not set correctly for user: {}", newUser.getUsername());
            throw new RuntimeException("Failed to set password for user");
        }
        
        logger.debug("User stored in memory: id={}, username={}, password length={}", 
                storedUser.getId(), storedUser.getUsername(), 
                storedUser.getPassword() != null ? storedUser.getPassword().length() : 0);
        
        // 立即保存到文件，确保用户数据持久化
        saveUsers();
        
        // 再次验证密码是否被正确保存
        try {
            // 创建一个专门用于验证的ObjectMapper
            ObjectMapper verifyMapper = new ObjectMapper();
            verifyMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            verifyMapper.setSerializationInclusion(JsonInclude.Include.ALWAYS);
            
            // 从文件中读取用户数据
            List<User> savedUsers = verifyMapper.readValue(new File(userDataFile), new TypeReference<List<User>>() {});
            
            // 查找刚刚注册的用户
            User savedUser = null;
            for (User u : savedUsers) {
                if (u.getId().equals(newUser.getId())) {
                    savedUser = u;
                    break;
                }
            }
            
            // 验证密码是否正确保存
            if (savedUser == null) {
                logger.error("User not found in saved data: {}", newUser.getUsername());
            } else if (!savedUser.hasPassword()) {
                logger.error("User {} has no password in saved data", newUser.getUsername());
                
                // 如果密码没有保存，尝试修复
                savedUser.setPassword(password);
                saveUsers();
                logger.info("Attempted to fix password for user: {}", newUser.getUsername());
            } else {
                logger.info("User {} password verified in saved data, length: {}", 
                        newUser.getUsername(), savedUser.getPassword().length());
            }
        } catch (Exception e) {
            logger.error("Error verifying saved user data", e);
        }
        
        logger.info("User registered: {}", newUser);
        
        return newUser;
    }
    
    /**
     * 修复所有用户的密码问题
     * 如果发现用户没有密码，设置一个默认密码
     */
    public void fixAllUserPasswords(String defaultPassword) {
        boolean needsSave = false;
        
        for (User user : userMap.values()) {
            if (!user.hasPassword()) {
                logger.warn("User {} has no password, setting default password", user.getUsername());
                user.setPassword(defaultPassword);
                needsSave = true;
            }
        }
        
        if (needsSave) {
            saveUsers();
            logger.info("Fixed passwords for users with missing passwords");
        }
    }
    
    @Override
    public User authenticateUser(String username, String password) {
        logger.debug("Authenticating user: {}", username);
        
        if (username == null || username.isEmpty() || password == null || password.isEmpty()) {
            logger.debug("Authentication failed: username or password is empty");
            return null;
        }
        
        // 尝试通过用户名查找用户
        User user = usernameMap.get(username.toLowerCase());
        
        // 如果通过用户名找不到，尝试通过电子邮件查找
        if (user == null) {
            logger.debug("User not found by username, trying to find by email: {}", username);
            // 遍历所有用户，查找匹配的电子邮件
            for (User u : userMap.values()) {
                if (u.getEmail() != null && u.getEmail().equalsIgnoreCase(username)) {
                    user = u;
                    logger.debug("User found by email: {}", username);
                    break;
                }
            }
        }
        
        if (user == null) {
            logger.debug("Authentication failed: user not found: {}", username);
            return null;
        }
        
        logger.debug("Found user: {}, comparing passwords...", user.getUsername());
        logger.debug("Input password length: {}, stored password length: {}", 
                password.length(), user.getPassword() != null ? user.getPassword().length() : 0);
        
        // 检查用户是否有密码
        if (!user.hasPassword()) {
            logger.warn("User {} has no password set", user.getUsername());
            return null;
        }
        
        // 打印密码的前两个字符进行比较（仅用于调试）
        if (user.getPassword() != null && user.getPassword().length() > 0 && 
            password != null && password.length() > 0) {
            logger.debug("Password comparison: input starts with '{}', stored starts with '{}'", 
                    password.charAt(0), user.getPassword().charAt(0));
        }
        
        // In a real application, you would use a password encoder
        if (password.equals(user.getPassword())) {
            logger.debug("Authentication successful for user: {}", user.getUsername());
            return user;
        }
        
        logger.debug("Authentication failed: incorrect password for user: {}", user.getUsername());
        return null;
    }
    
    @Override
    public User getUserById(String userId) {
        if (userId == null) {
            return null;
        }
        return userMap.get(userId);
    }
    
    @Override
    public User getUserByUsername(String username) {
        if (username == null) {
            return null;
        }
        return usernameMap.get(username.toLowerCase());
    }
    
    @Override
    public User updateUser(User user) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("User or ID is null");
        }
        
        User existingUser = getUserById(user.getId());
        if (existingUser == null) {
            throw new IllegalArgumentException("User not found with ID: " + user.getId());
        }
        
        // Handle username change
        if (!existingUser.getUsername().equals(user.getUsername())) {
            // Check if new username already exists
            if (usernameMap.containsKey(user.getUsername().toLowerCase()) && 
                !usernameMap.get(user.getUsername().toLowerCase()).getId().equals(user.getId())) {
                throw new IllegalArgumentException("Username already exists");
            }
            
            // Remove old username mapping
            usernameMap.remove(existingUser.getUsername().toLowerCase());
            
            // Add new username mapping
            usernameMap.put(user.getUsername().toLowerCase(), user);
        }
        
        // Update fields
        existingUser.setUsername(user.getUsername());
        existingUser.setEmail(user.getEmail());
        existingUser.setFullName(user.getFullName());
        existingUser.setEnabled(user.isEnabled());
        
        // Only update password if provided
        if (user.getPassword() != null && !user.getPassword().isEmpty()) {
            existingUser.setPassword(user.getPassword());
        }
        
        // Store updated user
        userMap.put(existingUser.getId(), existingUser);
        
        // Save to file
        saveUsers();
        
        return existingUser;
    }
    
    @Override
    public boolean deleteUser(String userId) {
        User user = getUserById(userId);
        if (user == null) {
            return false;
        }
        
        // Remove user
        userMap.remove(userId);
        usernameMap.remove(user.getUsername().toLowerCase());
        
        // Save to file
        saveUsers();
        
        logger.info("User deleted: {}", user);
        
        return true;
    }
    
    @Override
    public boolean hasStorageAvailable(String userId, long fileSize) {
        // 不再检查存储限制，永远返回true，允许无限制上传
        return true;
        
        /* 原代码（已注释）
        User user = getUserById(userId);
        if (user == null) {
            return false;
        }
        
        return user.getStorageLimit() - user.getStorageUsed() >= fileSize;
        */
    }
    
    @Override
    public void updateStorageUsed(String userId, long size, boolean add) {
        User user = getUserById(userId);
        if (user == null) {
            return;
        }
        
        long currentUsage = user.getTotalStorageUsed();
        long newUsage;
        
        if (add) {
            newUsage = currentUsage + size;
        } else {
            newUsage = currentUsage - size;
            if (newUsage < 0) {
                newUsage = 0;
            }
        }
        
        user.setTotalStorageUsed(newUsage);
        userMap.put(userId, user);
        
        // Save to file
        saveUsers();
    }
    
    @Override
    public List<User> getAllUsers() {
        return new ArrayList<>(userMap.values());
    }
    
    @Override
    public User updateStorageLimit(String userId, long storageLimit) {
        User user = getUserById(userId);
        if (user == null) {
            throw new IllegalArgumentException("User not found with ID: " + userId);
        }
        
        if (storageLimit < 0) {
            throw new IllegalArgumentException("Storage limit cannot be negative");
        }
        
        user.setStorageLimit(storageLimit);
        userMap.put(userId, user);
        
        // Save to file
        saveUsers();
        
        return user;
    }
} 