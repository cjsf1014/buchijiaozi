package com.filetransfer.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 文件监控服务实现类
 */
@Service
public class FileMonitoringServiceImpl implements FileMonitoringService, InitializingBean, DisposableBean {
    
    private static final Logger logger = LoggerFactory.getLogger(FileMonitoringServiceImpl.class);
    
    private long monitoringInterval = 60000; // 默认60秒
    
    private ScheduledExecutorService scheduler;
    
    private boolean isRunning = false;
    
    @Autowired
    private FileService fileService;
    
    @Autowired
    private TransferService transferService;
    
    @Override
    public void startMonitoring() {
        if (isRunning) {
            logger.info("File monitoring service is already running");
            return;
        }
        
        logger.info("Starting file monitoring service with interval: {} ms", monitoringInterval);
        
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(this::monitoringTask, 0, monitoringInterval, TimeUnit.MILLISECONDS);
        
        isRunning = true;
    }
    
    @Override
    public void stopMonitoring() {
        if (!isRunning) {
            return;
        }
        
        logger.info("Stopping file monitoring service");
        
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        isRunning = false;
    }
    
    @Override
    public int cleanupExpiredFiles() {
        logger.info("Cleaning up expired files");
        
        // 清理过期文件
        int filesDeleted = fileService.cleanupExpiredFiles();
        
        // 清理过期传输请求
        int transfersExpired = transferService.cleanupExpiredTransferRequests();
        
        logger.info("Cleanup completed: {} files deleted, {} transfers expired", filesDeleted, transfersExpired);
        
        return filesDeleted;
    }
    
    @Override
    public void setMonitoringInterval(long monitoringInterval) {
        this.monitoringInterval = monitoringInterval;
        
        // 如果服务正在运行，重启以应用新的间隔时间
        if (isRunning) {
            stopMonitoring();
            startMonitoring();
        }
    }
    
    @Override
    public long getMonitoringInterval() {
        return monitoringInterval;
    }
    
    /**
     * 监控任务，定期执行
     */
    private void monitoringTask() {
        try {
            logger.debug("Executing monitoring task");
            
            // 清理过期文件
            cleanupExpiredFiles();
            
            // 可以添加其他监控任务，如检查磁盘空间等
            
        } catch (Exception e) {
            logger.error("Error executing monitoring task", e);
        }
    }
    
    @Override
    public void afterPropertiesSet() throws Exception {
        // 在Spring容器启动时自动启动监控服务
        startMonitoring();
    }
    
    @Override
    public void destroy() throws Exception {
        // 在Spring容器关闭时自动停止监控服务
        stopMonitoring();
    }
} 