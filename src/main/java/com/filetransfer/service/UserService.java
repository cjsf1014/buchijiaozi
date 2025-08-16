package com.filetransfer.service;

import com.filetransfer.model.User;

import java.util.List;

/**
 * Service interface for user operations
 */
public interface UserService {
    
    /**
     * Register a new user
     * 
     * @param user The user to register
     * @return The registered user
     */
    User registerUser(User user);
    
    /**
     * Authenticate a user
     * 
     * @param username The username
     * @param password The password
     * @return The authenticated user, or null if authentication fails
     */
    User authenticateUser(String username, String password);
    
    /**
     * Get a user by ID
     * 
     * @param userId The ID of the user
     * @return The user
     */
    User getUserById(String userId);
    
    /**
     * Get a user by username
     * 
     * @param username The username
     * @return The user
     */
    User getUserByUsername(String username);
    
    /**
     * Update a user
     * 
     * @param user The updated user
     * @return The updated user
     */
    User updateUser(User user);
    
    /**
     * Delete a user
     * 
     * @param userId The ID of the user to delete
     * @return True if deleted successfully, false otherwise
     */
    boolean deleteUser(String userId);
    
    /**
     * Check if a user has enough storage available
     * 
     * @param userId The ID of the user
     * @param fileSize The size of the file to store
     * @return True if enough storage is available, false otherwise
     */
    boolean hasStorageAvailable(String userId, long fileSize);
    
    /**
     * Update the storage used by a user
     * 
     * @param userId The ID of the user
     * @param size The size to add or subtract
     * @param add True to add, false to subtract
     */
    void updateStorageUsed(String userId, long size, boolean add);
    
    /**
     * Get all users
     * 
     * @return List of all users
     */
    List<User> getAllUsers();
    
    /**
     * Update a user's storage limit
     * 
     * @param userId The ID of the user
     * @param storageLimit The new storage limit
     * @return The updated user
     */
    User updateStorageLimit(String userId, long storageLimit);
} 