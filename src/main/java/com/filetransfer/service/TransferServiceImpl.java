package com.filetransfer.service;

import com.filetransfer.model.TransferRequest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Implementation of the TransferService interface
 */
@Service
public class TransferServiceImpl implements TransferService {
    
    private static final Logger logger = LoggerFactory.getLogger(TransferServiceImpl.class);
    
    @Value("${file.upload.directory}")
    private String baseDirectory;
    
    private String transferRequestFile;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Value("${file.expiry.days:30}")
    private int defaultExpiryDays;
    
    @Autowired
    private FileService fileService;
    
    @Autowired
    private UserService userService;
    
    // In-memory cache for transfer requests
    private final Map<String, TransferRequest> transferRequestMap = new ConcurrentHashMap<>();
    
    @PostConstruct
    public void init() {
        // 确保基础目录存在
        File baseDir = new File(baseDirectory);
        if (!baseDir.exists()) {
            baseDir.mkdirs();
        }
        
        // 设置传输请求数据文件路径
        transferRequestFile = baseDirectory + File.separator + "transferRequests.json";
        
        // 加载传输请求数据
        loadTransferRequests();
    }
    
    /**
     * 从文件加载传输请求数据
     */
    private void loadTransferRequests() {
        File file = new File(transferRequestFile);
        if (!file.exists()) {
            logger.info("Transfer request data file does not exist, will be created when requests are made");
            return;
        }
        
        try {
            List<TransferRequest> requests = objectMapper.readValue(file, new TypeReference<List<TransferRequest>>() {});
            
            // 清空当前缓存
            transferRequestMap.clear();
            
            // 将传输请求添加到缓存
            for (TransferRequest request : requests) {
                transferRequestMap.put(request.getId(), request);
            }
            
            logger.info("Loaded {} transfer requests from file", requests.size());
        } catch (IOException e) {
            logger.error("Failed to load transfer request data", e);
        }
    }
    
    /**
     * 将传输请求数据保存到文件
     */
    private void saveTransferRequests() {
        try {
            List<TransferRequest> requests = new ArrayList<>(transferRequestMap.values());
            objectMapper.writeValue(new File(transferRequestFile), requests);
            logger.info("Saved {} transfer requests to file", requests.size());
        } catch (IOException e) {
            logger.error("Failed to save transfer request data", e);
        }
    }
    
    @Override
    public TransferRequest createTransferRequest(TransferRequest transferRequest) {
        if (transferRequest == null) {
            throw new IllegalArgumentException("Transfer request cannot be null");
        }
        
        if (transferRequest.getFileId() == null || transferRequest.getFileId().isEmpty()) {
            throw new IllegalArgumentException("File ID cannot be empty");
        }
        
        if (transferRequest.getSenderId() == null || transferRequest.getSenderId().isEmpty()) {
            throw new IllegalArgumentException("Sender ID cannot be empty");
        }
        
        // Validate file exists
        if (fileService.getFileInfo(transferRequest.getFileId()) == null) {
            throw new IllegalArgumentException("File not found with ID: " + transferRequest.getFileId());
        }
        
        // Generate ID if not provided
        if (transferRequest.getId() == null) {
            transferRequest.setId(UUID.randomUUID().toString());
        }
        
        // Set default expiry date if not provided
        if (transferRequest.getExpiryDate() == null) {
            Calendar calendar = Calendar.getInstance();
            calendar.add(Calendar.DAY_OF_MONTH, defaultExpiryDays);
            transferRequest.setExpiryDate(calendar.getTime());
        }
        
        // Set status to active
        transferRequest.setStatus("ACTIVE");
        
        // Store transfer request in memory cache
        transferRequestMap.put(transferRequest.getId(), transferRequest);
        
        // Save to file
        saveTransferRequests();
        
        logger.info("Transfer request created: {}", transferRequest);
        
        return transferRequest;
    }
    
    @Override
    public TransferRequest getTransferRequest(String transferId) {
        if (transferId == null) {
            return null;
        }
        return transferRequestMap.get(transferId);
    }
    
    @Override
    public List<TransferRequest> getSentTransferRequests(String userId) {
        if (userId == null) {
            return Collections.emptyList();
        }
        
        return transferRequestMap.values().stream()
                .filter(transferRequest -> userId.equals(transferRequest.getSenderId()))
                .collect(Collectors.toList());
    }
    
    @Override
    public List<TransferRequest> getReceivedTransferRequests(String userId) {
        if (userId == null) {
            return Collections.emptyList();
        }
        
        return transferRequestMap.values().stream()
                .filter(transferRequest -> userId.equals(transferRequest.getRecipientId()))
                .collect(Collectors.toList());
    }
    
    @Override
    public boolean cancelTransferRequest(String transferId, String userId) {
        TransferRequest transferRequest = getTransferRequest(transferId);
        if (transferRequest == null) {
            return false;
        }
        
        // Check if user is the sender
        if (!transferRequest.getSenderId().equals(userId)) {
            throw new IllegalArgumentException("Only the sender can cancel a transfer request");
        }
        
        // Set status to cancelled
        transferRequest.setStatus("CANCELLED");
        
        // Update transfer request
        transferRequestMap.put(transferId, transferRequest);
        
        // Save to file
        saveTransferRequests();
        
        logger.info("Transfer request cancelled: {}", transferRequest);
        
        return true;
    }
    
    @Override
    public TransferRequest accessTransferRequest(String transferId, String accessCode) {
        TransferRequest transferRequest = getTransferRequest(transferId);
        if (transferRequest == null) {
            return null;
        }
        
        // Check access code
        if (!transferRequest.getAccessCode().equals(accessCode)) {
            return null;
        }
        
        // Check if transfer is active
        if (!transferRequest.isActive()) {
            return transferRequest; // Return the inactive transfer for status checking
        }
        
        // Increment download count
        transferRequest.setDownloadCount(transferRequest.getDownloadCount() + 1);
        
        // Update transfer request
        transferRequestMap.put(transferId, transferRequest);
        
        // Save to file
        saveTransferRequests();
        
        return transferRequest;
    }
    
    @Override
    public TransferRequest updateTransferRequest(TransferRequest transferRequest) {
        if (transferRequest == null || transferRequest.getId() == null) {
            throw new IllegalArgumentException("Transfer request or ID is null");
        }
        
        TransferRequest existingTransferRequest = getTransferRequest(transferRequest.getId());
        if (existingTransferRequest == null) {
            throw new IllegalArgumentException("Transfer request not found with ID: " + transferRequest.getId());
        }
        
        // Update fields
        existingTransferRequest.setStatus(transferRequest.getStatus());
        existingTransferRequest.setExpiryDate(transferRequest.getExpiryDate());
        existingTransferRequest.setDownloadLimit(transferRequest.getDownloadLimit());
        existingTransferRequest.setNotifyOnDownload(transferRequest.isNotifyOnDownload());
        existingTransferRequest.setMessage(transferRequest.getMessage());
        
        // Store updated transfer request
        transferRequestMap.put(existingTransferRequest.getId(), existingTransferRequest);
        
        // Save to file
        saveTransferRequests();
        
        return existingTransferRequest;
    }
    
    @Override
    public int cleanupExpiredTransferRequests() {
        int count = 0;
        Date now = new Date();
        
        List<String> expiredTransferIds = transferRequestMap.values().stream()
                .filter(transferRequest -> transferRequest.getExpiryDate() != null && 
                        transferRequest.getExpiryDate().before(now))
                .map(TransferRequest::getId)
                .collect(Collectors.toList());
        
        for (String transferId : expiredTransferIds) {
            TransferRequest transferRequest = getTransferRequest(transferId);
            if (transferRequest != null) {
                transferRequest.setStatus("EXPIRED");
                transferRequestMap.put(transferId, transferRequest);
                count++;
                
                logger.info("Transfer request expired: {}", transferRequest);
            }
        }
        
        // 如果有传输请求过期，保存到文件
        if (count > 0) {
            saveTransferRequests();
        }
        
        return count;
    }
} 