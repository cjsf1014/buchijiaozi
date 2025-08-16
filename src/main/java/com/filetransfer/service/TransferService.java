package com.filetransfer.service;

import com.filetransfer.model.TransferRequest;

import java.util.List;

/**
 * Service interface for file transfer operations
 */
public interface TransferService {
    
    /**
     * Create a new transfer request
     * 
     * @param transferRequest The transfer request to create
     * @return The created TransferRequest
     */
    TransferRequest createTransferRequest(TransferRequest transferRequest);
    
    /**
     * Get a transfer request by ID
     * 
     * @param transferId The ID of the transfer request
     * @return The TransferRequest
     */
    TransferRequest getTransferRequest(String transferId);
    
    /**
     * Get transfer requests sent by a user
     * 
     * @param userId The ID of the user
     * @return List of TransferRequest objects
     */
    List<TransferRequest> getSentTransferRequests(String userId);
    
    /**
     * Get transfer requests received by a user
     * 
     * @param userId The ID of the user
     * @return List of TransferRequest objects
     */
    List<TransferRequest> getReceivedTransferRequests(String userId);
    
    /**
     * Cancel a transfer request
     * 
     * @param transferId The ID of the transfer request
     * @param userId The ID of the user making the request
     * @return True if cancelled successfully, false otherwise
     */
    boolean cancelTransferRequest(String transferId, String userId);
    
    /**
     * Access a transfer request using an access code
     * 
     * @param transferId The ID of the transfer request
     * @param accessCode The access code
     * @return The TransferRequest if access is granted, null otherwise
     */
    TransferRequest accessTransferRequest(String transferId, String accessCode);
    
    /**
     * Update a transfer request
     * 
     * @param transferRequest The updated TransferRequest
     * @return The updated TransferRequest
     */
    TransferRequest updateTransferRequest(TransferRequest transferRequest);
    
    /**
     * Clean up expired transfer requests
     * 
     * @return Number of transfer requests deleted
     */
    int cleanupExpiredTransferRequests();
} 