package com.filetransfer.controller;

import com.filetransfer.model.ApiResponse;
import com.filetransfer.model.User;
import com.filetransfer.service.UserService;
import com.filetransfer.service.UserServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller for debugging purposes
 */
@RestController
@RequestMapping("/debug")
public class DebugController {
    
    private static final Logger logger = LoggerFactory.getLogger(DebugController.class);
    
    @Autowired
    private UserService userService;
    
    /**
     * Get all users (for debugging)
     */
    @GetMapping("/users")
    public ResponseEntity<ApiResponse> getAllUsers() {
        logger.info("Getting all users for debugging");
        List<User> users = userService.getAllUsers();
        
        // Remove passwords before returning
        for (User user : users) {
            user.setPassword(null);
        }
        
        return ResponseEntity.ok(ApiResponse.success("Users retrieved", users));
    }
    
    /**
     * Reset password for a user
     */
    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse> resetPassword(
            @RequestParam String identifier,
            @RequestParam String newPassword) {
        
        logger.info("Resetting password for user: {}", identifier);
        
        // Find user by username or email
        User user = userService.getUserByUsername(identifier);
        if (user == null) {
            // Try to find by email
            for (User u : userService.getAllUsers()) {
                if (u.getEmail() != null && u.getEmail().equalsIgnoreCase(identifier)) {
                    user = u;
                    break;
                }
            }
        }
        
        if (user == null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("User not found: " + identifier));
        }
        
        // Update password
        user.setPassword(newPassword);
        userService.updateUser(user);
        
        logger.info("Password reset successfully for user: {}", user.getUsername());
        
        return ResponseEntity.ok(ApiResponse.success("Password reset successfully"));
    }
    
    /**
     * Test register and login flow
     */
    @PostMapping("/test-register")
    public ResponseEntity<ApiResponse> testRegister(
            @RequestParam String username,
            @RequestParam String password,
            @RequestParam String email) {
        
        logger.info("Testing registration flow for username: {}", username);
        
        // Create user object
        User user = new User();
        user.setUsername(username);
        user.setPassword(password);
        user.setEmail(email);
        
        // Log password before registration
        logger.debug("Password before registration: {}", password);
        
        // Register user
        User registeredUser = userService.registerUser(user);
        
        // Log password after storing in memory
        logger.debug("Password after storing in memory: {}", registeredUser.getPassword());
        
        // Try to authenticate immediately
        User authenticatedUser = userService.authenticateUser(username, password);
        
        if (authenticatedUser == null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Registration succeeded but immediate authentication failed"));
        }
        
        return ResponseEntity.ok(ApiResponse.success("Registration and authentication successful"));
    }
    
    /**
     * Fix all users' passwords
     */
    @PostMapping("/fix-passwords")
    public ResponseEntity<ApiResponse> fixPasswords(@RequestParam String defaultPassword) {
        logger.info("Fixing passwords for all users with default password: {}", defaultPassword);
        
        if (userService instanceof UserServiceImpl) {
            ((UserServiceImpl) userService).fixAllUserPasswords(defaultPassword);
            return ResponseEntity.ok(ApiResponse.success("Fixed passwords for users with missing passwords"));
        } else {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("UserService is not an instance of UserServiceImpl"));
        }
    }
} 