package com.filetransfer.service;

/**
 * 文件监控服务接口
 * 负责监控文件系统变化、清理过期文件等
 */
public interface FileMonitoringService {
    
    /**
     * 启动监控服务
     */
    void startMonitoring();
    
    /**
     * 停止监控服务
     */
    void stopMonitoring();
    
    /**
     * 清理过期文件
     * 
     * @return 清理的文件数量
     */
    int cleanupExpiredFiles();
    
    /**
     * 设置监控间隔时间（毫秒）
     * 
     * @param monitoringInterval 监控间隔（毫秒）
     */
    void setMonitoringInterval(long monitoringInterval);
    
    /**
     * 获取监控间隔时间（毫秒）
     * 
     * @return 监控间隔（毫秒）
     */
    long getMonitoringInterval();
} 