package com.filetransfer.service;

import com.filetransfer.model.FileInfo;
import com.filetransfer.util.FileUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.PostConstruct;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Implementation of the FileService interface
 */
@Service
public class FileServiceImpl implements FileService {
    
    private static final Logger logger = LoggerFactory.getLogger(FileServiceImpl.class);
    
    @Value("${file.upload.directory}")
private String uploadDirectory;

@Value("${file.user.files.directory:${file.upload.directory}/user_files}")
private String userFilesDirectory;

private String fileInfoFile;
private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Value("${file.expiry.days:30}")
    private int fileExpiryDays;
    
    @Value("${file.chunk.size:5242880}")
    private int chunkSize;
    
    @Value("${file.supported.types:image/*,video/*,audio/*,application/pdf}")
    private String supportedFileTypes;
    
    private List<String> supportedTypes;
    
    // In-memory cache for file information
    private final Map<String, FileInfo> fileInfoMap = new ConcurrentHashMap<>();
    
    // In-memory storage for tracking file chunks (should be replaced with a database in production)
    private final Map<String, Map<Integer, Boolean>> chunkTracker = new ConcurrentHashMap<>();
    
    @Autowired
    private UserService userService;
    
    @PostConstruct
    public void init() {
        logger.info("初始化文件服务...");
        
        try {
            // 确保uploadDirectory不为null
            if (uploadDirectory == null || uploadDirectory.trim().isEmpty()) {
                // 使用默认目录
                String catalinaBase = System.getProperty("catalina.base");
                if (catalinaBase != null) {
                    uploadDirectory = catalinaBase + "/webapps/file-transfer/WEB-INF/data/";
                } else {
                    // 如果catalina.base也不可用，使用临时目录
                    uploadDirectory = System.getProperty("java.io.tmpdir") + "/file-transfer-data";
                }
                logger.warn("上传目录未设置，使用默认目录: {}", uploadDirectory);
            }
            
            logger.info("使用上传目录: {}", uploadDirectory);
            
            // 确保上传目录存在
            File uploadDir = new File(uploadDirectory);
            if (!uploadDir.exists()) {
                logger.info("上传目录不存在，尝试创建: {}", uploadDirectory);
                boolean created = uploadDir.mkdirs();
                if (!created) {
                    logger.warn("无法创建上传目录: {}", uploadDirectory);
                    
                    // 尝试使用绝对路径
                    String absolutePath = uploadDir.getAbsolutePath();
                    logger.info("尝试使用绝对路径创建目录: {}", absolutePath);
                    uploadDir = new File(absolutePath);
                    created = uploadDir.mkdirs();
                    
                    if (!created) {
                        // 尝试在Tomcat的webapps目录下创建
                        String catalinaBase = System.getProperty("catalina.base");
                        if (catalinaBase != null) {
                            String webappsPath = catalinaBase + "/webapps/file-transfer/WEB-INF/data";
                            logger.info("尝试在Tomcat webapps下创建目录: {}", webappsPath);
                            uploadDir = new File(webappsPath);
                            created = uploadDir.mkdirs();
                            if (created) {
                                uploadDirectory = webappsPath;
                                logger.info("成功在Tomcat webapps下创建目录: {}", uploadDirectory);
                            }
                        }
                    }
                    
                    if (!created) {
                        logger.error("所有尝试创建上传目录均失败!");
                    }
                } else {
                    logger.info("成功创建上传目录: {}", uploadDirectory);
                }
            } else {
                logger.info("上传目录已存在: {}", uploadDirectory);
            }
            
            // 检查目录权限
            if (!uploadDir.canWrite()) {
                logger.warn("上传目录没有写入权限: {}", uploadDirectory);
                // 尝试设置写入权限（在不同操作系统上可能需要不同的方法）
                boolean setWritable = uploadDir.setWritable(true, false);
                if (!setWritable) {
                    // 在某些系统上，可能需要设置所有者权限
                    setWritable = uploadDir.setWritable(true, true);
                }
                if (setWritable) {
                    logger.info("已成功设置上传目录的写入权限");
                } else {
                    logger.error("无法设置上传目录的写入权限，可能会导致上传失败");
                    // 提供更详细的错误信息
                    logger.error("请手动检查目录权限或以管理员身份运行应用");
                }
            }
            
            // 设置文件信息存储路径
            fileInfoFile = uploadDirectory + File.separator + "fileInfo.json";
            logger.info("文件信息存储路径: {}", fileInfoFile);
            
            // 确保fileInfo.json文件存在
            File infoFile = new File(fileInfoFile);
            if (!infoFile.exists()) {
                try {
                    // 确保父目录存在
                    if (!infoFile.getParentFile().exists()) {
                        infoFile.getParentFile().mkdirs();
                    }
                    
                    boolean created = infoFile.createNewFile();
                    if (created) {
                        // 初始化为空的JSON数组
                        try (FileWriter writer = new FileWriter(infoFile)) {
                            writer.write("[]");
                        }
                        logger.info("已创建并初始化fileInfo.json文件");
                    } else {
                        logger.error("无法创建fileInfo.json文件");
                    }
                } catch (IOException e) {
                    logger.error("创建fileInfo.json文件时出错: {}", e.getMessage());
                }
            }
            
            // 输出当前使用的上传目录
            logger.info("当前使用的上传目录: {}", uploadDirectory);
            logger.info("上传目录是否存在: {}", uploadDir.exists());
            logger.info("上传目录是否可写: {}", uploadDir.canWrite());
            logger.info("文件信息存储文件路径: {}", fileInfoFile);
            logger.info("文件信息存储文件是否存在: {}", infoFile.exists());
            
            // 确保用户文件目录存在
            if (userFilesDirectory == null || userFilesDirectory.trim().isEmpty()) {
                userFilesDirectory = uploadDirectory + File.separator + "user_files";
            }
            
            File userFilesDir = new File(userFilesDirectory);
            if (!userFilesDir.exists()) {
                logger.info("用户文件目录不存在，尝试创建: {}", userFilesDirectory);
                boolean created = userFilesDir.mkdirs();
                if (!created) {
                    logger.error("无法创建用户文件目录: {}", userFilesDirectory);
                } else {
                    logger.info("成功创建用户文件目录: {}", userFilesDirectory);
                }
            } else {
                logger.info("用户文件目录已存在: {}", userFilesDirectory);
            }
            
            // 输出系统属性，帮助调试
            logger.info("系统属性 - user.dir: {}", System.getProperty("user.dir"));
            logger.info("系统属性 - catalina.base: {}", System.getProperty("catalina.base"));
            logger.info("系统属性 - user.home: {}", System.getProperty("user.home"));
            logger.info("系统属性 - java.io.tmpdir: {}", System.getProperty("java.io.tmpdir"));
            
        } catch (Exception e) {
            logger.error("初始化文件服务时出错: {}", e.getMessage(), e);
        }
        
        // 初始化支持的文件类型列表
        if (supportedFileTypes == null || supportedFileTypes.trim().isEmpty()) {
            supportedFileTypes = "*/*";  // 默认支持所有类型
        }
        
        supportedTypes = Arrays.asList(supportedFileTypes.split(","));
        logger.info("初始化文件类型支持: {}", supportedFileTypes);
        logger.info("支持的文件类型列表: {}", supportedTypes);
        
        // 加载文件信息
        try {
            List<FileInfo> loadedFiles = loadFileInfo();
            for (FileInfo fileInfo : loadedFiles) {
                fileInfoMap.put(fileInfo.getId(), fileInfo);
            }
        } catch (Exception e) {
            logger.error("加载文件信息失败: {}", e.getMessage(), e);
            // 初始化为空映射
            fileInfoMap.clear();
        }
    }
    
    public void setUploadDirectory(String uploadDirectory) {
        this.uploadDirectory = uploadDirectory;
        logger.info("设置上传目录: {}", uploadDirectory);
        
        // Create directory if it doesn't exist
        File directory = new File(uploadDirectory);
        if (!directory.exists()) {
            boolean created = directory.mkdirs();
            logger.info("创建上传目录: {} - {}", uploadDirectory, created ? "成功" : "失败");
            if (!created) {
                logger.error("无法创建上传目录，尝试使用绝对路径");
                // 尝试使用绝对路径
                try {
                    File absoluteDir = new File(uploadDirectory).getAbsoluteFile();
                    created = absoluteDir.mkdirs();
                    if (created) {
                        this.uploadDirectory = absoluteDir.getPath();
                        logger.info("使用绝对路径创建上传目录成功: {}", this.uploadDirectory);
                    } else {
                        logger.error("使用绝对路径创建上传目录仍然失败: {}", absoluteDir.getPath());
                        
                        // 尝试使用Tomcat的webapps目录
                        String catalinaBase = System.getProperty("catalina.base");
                        if (catalinaBase != null) {
                            File tomcatDir = new File(catalinaBase, "webapps/file-transfer/WEB-INF/data");
                            created = tomcatDir.mkdirs();
                            if (created) {
                                this.uploadDirectory = tomcatDir.getPath();
                                logger.info("使用Tomcat目录创建上传目录成功: {}", this.uploadDirectory);
                            } else {
                                logger.error("使用Tomcat目录创建上传目录失败: {}", tomcatDir.getPath());
                            }
                        }
                    }
                } catch (Exception e) {
                    logger.error("创建上传目录时发生异常", e);
                }
            }
        } else {
            logger.info("上传目录已存在: {}", uploadDirectory);
            // 检查目录是否可写
            if (!directory.canWrite()) {
                logger.error("上传目录不可写，请检查权限: {}", uploadDirectory);
                
                // 尝试修改权限（在Linux环境中可能有效）
                try {
                    boolean writable = directory.setWritable(true, false);
                    if (writable) {
                        logger.info("成功修改上传目录权限为可写");
                    } else {
                        logger.error("无法修改上传目录权限为可写");
                    }
                } catch (Exception e) {
                    logger.error("修改目录权限时发生异常", e);
                }
            }
        }
        
        // 设置文件信息存储路径
        fileInfoFile = uploadDirectory + File.separator + "fileInfo.json";
        logger.info("文件信息存储路径: {}", fileInfoFile);
        
        // 确保fileInfo.json文件存在
        try {
            File infoFile = new File(fileInfoFile);
            if (!infoFile.exists()) {
                boolean created = infoFile.createNewFile();
                if (created) {
                    // 写入空数组作为初始内容
                    try (FileWriter writer = new FileWriter(infoFile)) {
                        writer.write("[]");
                    }
                    logger.info("创建文件信息存储文件成功: {}", fileInfoFile);
                } else {
                    logger.error("无法创建文件信息存储文件: {}", fileInfoFile);
                }
            }
        } catch (IOException e) {
            logger.error("创建文件信息存储文件失败: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 从文件加载文件信息
     */
    private synchronized List<FileInfo> loadFileInfo() {
        try {
            // 检查fileInfoFile是否为null
            if (fileInfoFile == null) {
        if (uploadDirectory == null) {
                    logger.error("无法加载文件信息：uploadDirectory和fileInfoFile均为null");
                    return new ArrayList<>();
                }
                fileInfoFile = uploadDirectory + File.separator + "fileInfo.json";
                logger.info("fileInfoFile为null，已重新设置为: {}", fileInfoFile);
        }
        
        File file = new File(fileInfoFile);
        if (!file.exists()) {
                logger.warn("文件信息文件不存在: {}, 将创建新文件", fileInfoFile);
                saveFileInfo(new ArrayList<>());
                return new ArrayList<>();
            }
            
            if (file.length() == 0) {
                logger.warn("文件信息文件为空: {}, 将初始化为空列表", fileInfoFile);
                saveFileInfo(new ArrayList<>());
                return new ArrayList<>();
            }
            
            // 配置ObjectMapper忽略未知属性
            ObjectMapper mapper = new ObjectMapper();
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            List<FileInfo> fileInfoList = mapper.readValue(file, 
                    mapper.getTypeFactory().constructCollectionType(List.class, FileInfo.class));
            
            logger.info("成功加载文件信息，共 {} 个文件", fileInfoList.size());
            
            // 验证文件是否实际存在
            List<FileInfo> validFiles = new ArrayList<>();
            for (FileInfo info : fileInfoList) {
                if (info.getPath() == null) {
                    logger.warn("文件路径为null: {}", info.getId());
                    continue;
                }
                
                File actualFile = new File(info.getPath());
                if (actualFile.exists() && actualFile.isFile()) {
                    validFiles.add(info);
                } else {
                    logger.warn("文件不存在或不是文件: {}", info.getPath());
                }
            }
            
            // 如果有无效文件，更新文件信息
            if (validFiles.size() != fileInfoList.size()) {
                logger.warn("发现 {} 个无效文件，更新文件信息", fileInfoList.size() - validFiles.size());
                saveFileInfo(validFiles);
                return validFiles;
            }
            
            return fileInfoList;
        } catch (IOException e) {
            logger.error("加载文件信息失败: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }
    
    // 将文件信息保存到文件
    private synchronized void saveFileInfo(List<FileInfo> fileInfoList) {
        try {
            // 检查fileInfoFile是否为null
            if (fileInfoFile == null) {
                if (uploadDirectory == null) {
                    logger.error("无法保存文件信息：uploadDirectory和fileInfoFile均为null");
                    return;
                }
                fileInfoFile = uploadDirectory + File.separator + "fileInfo.json";
                logger.info("fileInfoFile为null，已重新设置为: {}", fileInfoFile);
            }
            
            logger.info("保存文件信息到: {}, 文件数量: {}", fileInfoFile, fileInfoList.size());
            
            // 创建文件的父目录（如果不存在）
            File file = new File(fileInfoFile);
            File parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                boolean created = parentDir.mkdirs();
                logger.info("创建父目录: {} - {}", parentDir.getAbsolutePath(), created ? "成功" : "失败");
                if (!created) {
                    logger.error("无法创建父目录: {}", parentDir.getAbsolutePath());
                    return;
                }
            }
            
            // 检查目录是否可写
            if (parentDir != null && !parentDir.canWrite()) {
                logger.error("父目录不可写: {}", parentDir.getAbsolutePath());
                return;
            }
            
            // 使用临时文件方式保存，确保原子性
            File tempFile = new File(fileInfoFile + ".tmp");
            try {
                // 使用Jackson序列化文件信息列表
                ObjectMapper mapper = new ObjectMapper();
                mapper.enable(SerializationFeature.INDENT_OUTPUT);
                // 配置ObjectMapper只序列化实际的字段，不序列化getter方法
                mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
                mapper.writeValue(tempFile, fileInfoList);
                
                // 原子性地替换原文件
                if (file.exists()) {
                    File backupFile = new File(fileInfoFile + ".bak");
                    if (backupFile.exists()) {
                        backupFile.delete();
                    }
                    file.renameTo(backupFile);
                }
                tempFile.renameTo(file);
                
                logger.info("文件信息保存成功，共 {} 个文件", fileInfoList.size());
            } catch (IOException e) {
                logger.error("保存文件信息到临时文件失败: {}", e.getMessage(), e);
                // 清理临时文件
                if (tempFile.exists()) {
                    tempFile.delete();
                }
            }
        } catch (Exception e) {
            logger.error("保存文件信息失败: {}", e.getMessage(), e);
        }
    }
    
    // 用于批量上传时的文件信息保存，确保一致性
    private synchronized void saveFileInfoAfterBatchUpload(List<FileInfo> fileInfoList) {
        // 在批量上传完成后，重新加载文件信息以确保一致性
        try {
            List<FileInfo> loadedFiles = loadFileInfo();
            Map<String, FileInfo> loadedFileMap = new HashMap<>();
            for (FileInfo fileInfo : loadedFiles) {
                loadedFileMap.put(fileInfo.getId(), fileInfo);
            }
            
            // 合并现有的文件信息和新上传的文件信息
            for (FileInfo fileInfo : fileInfoList) {
                loadedFileMap.put(fileInfo.getId(), fileInfo);
            }
            
            // 保存合并后的文件信息
            saveFileInfo(new ArrayList<>(loadedFileMap.values()));
            
            // 更新内存中的文件信息映射
            fileInfoMap.clear();
            fileInfoMap.putAll(loadedFileMap);
            
            logger.info("批量上传后文件信息保存完成，共 {} 个文件", loadedFileMap.size());
        } catch (Exception e) {
            logger.error("批量上传后保存文件信息失败: {}", e.getMessage(), e);
            // 如果合并失败，仍然保存当前的文件信息
            saveFileInfo(fileInfoList);
        }
    }
    
    @Override
    public FileInfo uploadFile(MultipartFile file, String userId) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }
        
        // 确保上传目录存在
        if (uploadDirectory == null || uploadDirectory.trim().isEmpty()) {
            String catalinaBase = System.getProperty("catalina.base");
            if (catalinaBase != null) {
                uploadDirectory = catalinaBase + "/webapps/file-transfer/WEB-INF/data";
            } else {
                uploadDirectory = System.getProperty("java.io.tmpdir") + "/file-transfer-data";
            }
            logger.warn("上传目录未设置，使用默认目录: {}", uploadDirectory);
        }
        
        // 确保用户文件目录存在
        if (userFilesDirectory == null || userFilesDirectory.trim().isEmpty()) {
            userFilesDirectory = uploadDirectory + File.separator + "user_files";
        }
        
        File userFilesDir = new File(userFilesDirectory);
        if (!userFilesDir.exists()) {
            boolean created = userFilesDir.mkdirs();
            if (!created) {
                throw new IOException("无法创建用户文件目录: " + userFilesDirectory);
            }
        }
        
        File uploadDir = new File(uploadDirectory);
        if (!uploadDir.exists()) {
            boolean created = uploadDir.mkdirs();
            if (!created) {
                throw new IOException("无法创建上传目录: " + uploadDirectory);
            }
        }
        
        if (!uploadDir.canWrite()) {
            boolean setWritable = uploadDir.setWritable(true);
            if (!setWritable) {
                logger.warn("无法设置上传目录的写入权限: {}", uploadDirectory);
            }
        }
        
        // 确保fileInfoFile不为null
        if (fileInfoFile == null) {
            fileInfoFile = uploadDirectory + File.separator + "fileInfo.json";
        }
        
        // 生成唯一文件ID
        String fileId = UUID.randomUUID().toString();
        
        // 获取原始文件名和扩展名
        String originalFileName = file.getOriginalFilename();
        String extension = "";
        
        // 处理中文文件名编码问题
        if (originalFileName != null) {
            try {
                // 尝试检测和修复文件名编码
                byte[] fileNameBytes = originalFileName.getBytes("ISO-8859-1");
                // 检查是否包含非ASCII字符
                boolean hasNonAscii = false;
                for (byte b : fileNameBytes) {
                    if (b < 0) {
                        hasNonAscii = true;
                        break;
                    }
                }
                
                if (hasNonAscii) {
                    // 尝试使用UTF-8解码
                    originalFileName = new String(fileNameBytes, "UTF-8");
                }
            } catch (Exception e) {
                logger.warn("处理文件名编码时出错: {}", e.getMessage());
            }
            
            if (originalFileName.contains(".")) {
                extension = originalFileName.substring(originalFileName.lastIndexOf("."));
            }
        }
        
        // 使用原始文件名存储，但确保文件名安全
        String safeFileName = originalFileName;
        if (safeFileName == null || safeFileName.isEmpty()) {
            safeFileName = fileId + extension;
        } else {
            // 处理文件名中的特殊字符，确保文件系统兼容性
            safeFileName = safeFileName.replaceAll("[<>:\"/\\\\|?*]", "_");
            // 限制文件名长度
            if (safeFileName.length() > 200) {
                safeFileName = safeFileName.substring(0, 200);
            }
        }
        
        // 记录原始文件名用于显示，但使用安全的文件名存储在磁盘上
        logger.debug("原始文件名: {}, 安全文件名: {}", originalFileName, safeFileName);
        
        // 构建保存路径 - 使用共享的用户文件目录
        String filePath = userFilesDirectory + File.separator + safeFileName;
        File destFile = new File(filePath);
        
        logger.info("保存文件到: {}", filePath);
        
        // 保存文件
        try {
            file.transferTo(destFile);
            
            // 检查文件是否成功写入
            if (!destFile.exists() || destFile.length() == 0) {
                throw new IOException("文件保存失败，文件不存在或大小为0");
            }
            
            // 创建文件信息对象
            FileInfo fileInfo = new FileInfo();
            fileInfo.setId(fileId);
            fileInfo.setUploadedBy(userId);
            fileInfo.setFileName(safeFileName); // 用于磁盘存储的安全文件名
            fileInfo.setOriginalFileName(originalFileName); // 原始文件名，用于显示
            fileInfo.setPath(filePath);
            fileInfo.setSize(file.getSize());
            fileInfo.setContentType(file.getContentType());
            fileInfo.setUploadDate(new Date());
            
            // 确保原始文件名不为空
            if (fileInfo.getOriginalFileName() == null || fileInfo.getOriginalFileName().isEmpty()) {
                fileInfo.setOriginalFileName(originalFileName);
            }
            
            logger.debug("文件信息 - ID: {}, 安全文件名: {}, 原始文件名: {}", 
                fileId, safeFileName, originalFileName);
            
            // 添加到文件信息映射
            fileInfoMap.put(fileId, fileInfo);
        
        // 保存文件信息到文件，使用同步块确保线程安全
        synchronized (this) {
            saveFileInfo(new ArrayList<>(fileInfoMap.values()));
        }
        
        logger.info("File uploaded: {}", fileInfo);
        return fileInfo;
        } catch (IOException e) {
            // 如果保存失败，删除可能部分写入的文件
            if (destFile.exists()) {
                boolean deleted = destFile.delete();
                if (deleted) {
                    logger.info("已删除部分写入的文件: {}", filePath);
                }
            }
            throw e;
        }
    }
    
    @Override
    public boolean uploadChunk(String fileId, MultipartFile chunk, int chunkNumber, int totalChunks, String userId) throws IOException {
        // Initialize chunk tracker for this file if not exists
        chunkTracker.putIfAbsent(fileId, new ConcurrentHashMap<>());
        Map<Integer, Boolean> fileChunks = chunkTracker.get(fileId);
        
        // Create temp directory for chunks if not exists
        String chunkDirectory = uploadDirectory + File.separator + "chunks" + File.separator + fileId;
        File directory = new File(chunkDirectory);
        if (!directory.exists()) {
            directory.mkdirs();
        }
        
        // Save chunk
        String chunkPath = chunkDirectory + File.separator + chunkNumber;
        File chunkFile = new File(chunkPath);
        chunk.transferTo(chunkFile);
        
        // Mark chunk as received
        fileChunks.put(chunkNumber, true);
        
        // Check if all chunks are received
        if (fileChunks.size() == totalChunks) {
            boolean allChunksReceived = true;
            for (int i = 0; i < totalChunks; i++) {
                if (!fileChunks.containsKey(i)) {
                    allChunksReceived = false;
                    break;
                }
            }
            
            if (allChunksReceived) {
                // Combine chunks
                FileInfo fileInfo = combineChunks(fileId, totalChunks, userId);
                
                // Clean up chunks
                for (int i = 0; i < totalChunks; i++) {
                    File file = new File(chunkDirectory + File.separator + i);
                    file.delete();
                }
                directory.delete();
                
                // Remove from chunk tracker
                chunkTracker.remove(fileId);
                
                return true;
            }
        }
        
        return false;
    }
    
    private FileInfo combineChunks(String fileId, int totalChunks, String userId) throws IOException {
        FileInfo fileInfo = getFileInfo(fileId);
        if (fileInfo == null) {
            fileInfo = new FileInfo();
            fileInfo.setId(fileId);
            fileInfo.setUploadedBy(userId);
            
            // Calculate expiry date
            Calendar calendar = Calendar.getInstance();
            calendar.add(Calendar.DAY_OF_MONTH, fileExpiryDays);
            fileInfo.setExpiryDate(calendar.getTime());
        }
        
        // 确保用户文件目录存在
        if (userFilesDirectory == null || userFilesDirectory.trim().isEmpty()) {
            userFilesDirectory = uploadDirectory + File.separator + "user_files";
        }
        
        File userFilesDir = new File(userFilesDirectory);
        if (!userFilesDir.exists()) {
            userFilesDir.mkdirs();
        }
        
        String chunkDirectory = uploadDirectory + File.separator + "chunks" + File.separator + fileId;
        // 使用原始文件名存储，确保文件名安全
        String originalFileName = fileInfo.getOriginalFileName();
        String safeFileName = originalFileName;
        if (safeFileName == null || safeFileName.isEmpty()) {
            safeFileName = fileId;
        } else {
            // 处理文件名中的特殊字符，确保文件系统兼容性
            safeFileName = safeFileName.replaceAll("[<>:\"/\\\\|?*]", "_");
            // 限制文件名长度
            if (safeFileName.length() > 200) {
                safeFileName = safeFileName.substring(0, 200);
            }
        }
        String outputPath = userFilesDirectory + File.separator + safeFileName;
        
        // Combine chunks
        try (FileOutputStream fos = new FileOutputStream(outputPath)) {
            long totalSize = 0;
            
            for (int i = 0; i < totalChunks; i++) {
                File chunkFile = new File(chunkDirectory + File.separator + i);
                byte[] chunkData = Files.readAllBytes(chunkFile.toPath());
                fos.write(chunkData);
                totalSize += chunkData.length;
            }
            
            fileInfo.setPath(outputPath);
            fileInfo.setSize(totalSize);
            fileInfo.setStatus("UPLOADED");
            
            // Calculate checksum
            fileInfo.setChecksum(calculateChecksum(new File(outputPath)));
            
            // Update user storage usage
            userService.updateStorageUsed(userId, totalSize, true);
            
            // Store file info
            fileInfoMap.put(fileInfo.getId(), fileInfo);
            
            // 保存文件信息到文件，使用同步块确保线程安全
            synchronized (this) {
                saveFileInfo(new ArrayList<>(fileInfoMap.values()));
            }
        }
        
        return fileInfo;
    }
    
    @Override
    public File downloadFile(String fileId) throws IOException {
        FileInfo fileInfo = getFileInfo(fileId);
        if (fileInfo == null) {
            throw new FileNotFoundException("File not found with ID: " + fileId);
        }
        
        File file = new File(fileInfo.getPath());
        if (!file.exists()) {
            throw new FileNotFoundException("File not found at path: " + fileInfo.getPath());
        }
        
        // Update download count
        fileInfo.setDownloadCount(fileInfo.getDownloadCount() + 1);
        fileInfoMap.put(fileId, fileInfo);
        
        // 保存文件信息到文件
        saveFileInfo(new ArrayList<>(fileInfoMap.values()));
        
        return file;
    }
    
    @Override
    public FileInfo getFileInfo(String fileId) {
        return fileInfoMap.get(fileId);
    }
    
    @Override
    public List<FileInfo> getUserFiles(String userId) {
        try {
        List<FileInfo> userFiles = fileInfoMap.values().stream()
                .filter(fileInfo -> fileInfo.getUploadedBy().equals(userId))
                .collect(Collectors.toList());
        
        // 添加日志记录每个文件的信息，帮助调试
        logger.debug("获取用户文件列表，用户ID: {}, 文件数量: {}", userId, userFiles.size());
            
            // 检查文件是否存在于磁盘上
            List<FileInfo> validFiles = new ArrayList<>();
        for (FileInfo file : userFiles) {
                File physicalFile = new File(file.getPath());
                boolean exists = physicalFile.exists();
                
                logger.debug("文件信息: id={}, fileName={}, originalFileName={}, 路径={}, 是否存在={}",
                    file.getId(), 
                    file.getFileName(), 
                    file.getOriginalFileName(),
                        file.getPath(),
                        exists);
                
                if (exists) {
                    validFiles.add(file);
                } else {
                    logger.warn("文件不存在于磁盘，但存在于记录中: {}", file.getPath());
                    // 可以选择从fileInfoMap中移除不存在的文件记录
                    // fileInfoMap.remove(file.getId());
                }
            }
            
            // 如果有文件不存在，更新文件信息
            if (validFiles.size() < userFiles.size()) {
                logger.warn("有{}个文件不存在于磁盘，已从结果中移除", userFiles.size() - validFiles.size());
                // 可以选择保存更新后的文件信息
                // saveFileInfo();
            }
            
            return validFiles; // 返回存在的文件
        } catch (Exception e) {
            logger.error("获取用户文件列表失败", e);
            return new ArrayList<>(); // 返回空列表而不是抛出异常
        }
    }
    
    @Override
    public boolean deleteFile(String fileId, String userId) {
        FileInfo fileInfo = getFileInfo(fileId);
        if (fileInfo == null) {
            return false;
        }
        
        // Check if user is the owner
        if (!fileInfo.getUploadedBy().equals(userId)) {
            return false;
        }
        
        // Delete physical file
        File file = new File(fileInfo.getPath());
        boolean deleted = file.delete();
        
        if (deleted) {
            // Update user storage usage
            userService.updateStorageUsed(userId, fileInfo.getSize(), false);
            
            // Remove file info
            fileInfoMap.remove(fileId);
            
            // 保存文件信息到文件
            saveFileInfo(new ArrayList<>(fileInfoMap.values()));
            
            logger.info("File deleted: {}", fileInfo);
        }
        
        return deleted;
    }
    
    @Override
    public InputStream getFileAsStream(String fileId) throws IOException {
        FileInfo fileInfo = getFileInfo(fileId);
        if (fileInfo == null) {
            throw new FileNotFoundException("File not found with ID: " + fileId);
        }
        
        File file = new File(fileInfo.getPath());
        if (!file.exists()) {
            throw new FileNotFoundException("File not found at path: " + fileInfo.getPath());
        }
        
        return new FileInputStream(file);
    }
    
    @Override
    public FileInfo updateFileInfo(FileInfo fileInfo) {
        if (fileInfo == null || fileInfo.getId() == null) {
            throw new IllegalArgumentException("FileInfo or ID is null");
        }
        
        FileInfo existingFileInfo = getFileInfo(fileInfo.getId());
        if (existingFileInfo == null) {
            throw new IllegalArgumentException("File not found with ID: " + fileInfo.getId());
        }
        
        // Update fields
        existingFileInfo.setFileName(fileInfo.getFileName());
        existingFileInfo.setDescription(fileInfo.getDescription());
        existingFileInfo.setPublic(fileInfo.isPublic());
        
        // Store updated file info
        fileInfoMap.put(existingFileInfo.getId(), existingFileInfo);
        
        // 保存文件信息到文件
        saveFileInfo(new ArrayList<>(fileInfoMap.values()));
        
        return existingFileInfo;
    }
    
    @Override
    public List<FileInfo> searchFiles(String query, String userId) {
        if (query == null || query.isEmpty()) {
            return getUserFiles(userId);
        }
        
        String lowerQuery = query.toLowerCase();
        
        return fileInfoMap.values().stream()
                .filter(fileInfo -> fileInfo.getUploadedBy().equals(userId) &&
                        (fileInfo.getFileName().toLowerCase().contains(lowerQuery) ||
                         (fileInfo.getDescription() != null && fileInfo.getDescription().toLowerCase().contains(lowerQuery))))
                .collect(Collectors.toList());
    }
    
    @Override
    public String calculateChecksum(String fileId) throws IOException {
        FileInfo fileInfo = getFileInfo(fileId);
        if (fileInfo == null) {
            throw new FileNotFoundException("File not found with ID: " + fileId);
        }
        
        File file = new File(fileInfo.getPath());
        return calculateChecksum(file);
    }
    
    private String calculateChecksum(File file) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    md.update(buffer, 0, bytesRead);
                }
            }
            
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("Failed to calculate checksum", e);
        }
    }
    
    @Override
    public List<FileInfo> getPublicFiles() {
        return fileInfoMap.values().stream()
                .filter(FileInfo::isPublic)
                .collect(Collectors.toList());
    }
    
    @Override
    public FileInfo setFileVisibility(String fileId, boolean isPublic, String userId) {
        FileInfo fileInfo = getFileInfo(fileId);
        if (fileInfo == null) {
            throw new IllegalArgumentException("File not found with ID: " + fileId);
        }
        
        // Check if user is the owner
        if (!fileInfo.getUploadedBy().equals(userId)) {
            throw new IllegalArgumentException("User is not the owner of the file");
        }
        
        fileInfo.setPublic(isPublic);
        fileInfoMap.put(fileId, fileInfo);
        
        // 保存文件信息到文件
        saveFileInfo(new ArrayList<>(fileInfoMap.values()));
        
        return fileInfo;
    }
    
    @Override
    public int cleanupExpiredFiles() {
        int count = 0;
        Date now = new Date();
        
        List<String> expiredFileIds = fileInfoMap.values().stream()
                .filter(fileInfo -> fileInfo.getExpiryDate() != null && fileInfo.getExpiryDate().before(now))
                .map(FileInfo::getId)
                .collect(Collectors.toList());
        
        for (String fileId : expiredFileIds) {
            FileInfo fileInfo = getFileInfo(fileId);
            if (fileInfo != null) {
                File file = new File(fileInfo.getPath());
                if (file.delete()) {
                    // Update user storage usage
                    userService.updateStorageUsed(fileInfo.getUploadedBy(), fileInfo.getSize(), false);
                    
                    // Remove file info
                    fileInfoMap.remove(fileId);
                    count++;
                    
                    logger.info("Expired file deleted: {}", fileInfo);
                }
            }
        }
        
        // 如果有文件被删除，保存文件信息到文件
        if (count > 0) {
            saveFileInfo(new ArrayList<>(fileInfoMap.values()));
        }
        
        return count;
    }
    
    @Override
    public boolean isPreviewable(String fileId) {
        FileInfo fileInfo = getFileInfo(fileId);
        if (fileInfo == null) {
            return false;
        }
        
        String contentType = fileInfo.getContentType();
        if (contentType == null) {
            return false;
        }
        
        // 检查文件大小，太大的文件不适合预览
        if (fileInfo.getSize() > 50 * 1024 * 1024) { // 50MB
            // 但图片和PDF可以例外，因为浏览器可以高效处理
            if (!contentType.startsWith("image/") && !contentType.equals("application/pdf")) {
                logger.debug("文件过大，不适合预览: {}, 大小: {}", fileInfo.getFileName(), fileInfo.getSize());
                return false;
            }
        }
        
        // 可预览的文件类型
        return contentType.startsWith("image/") ||  // 图片
               contentType.startsWith("video/") ||  // 视频
               contentType.startsWith("audio/") ||  // 音频
               contentType.equals("application/pdf") ||  // PDF
               contentType.startsWith("text/") ||  // 文本文件
               // 添加HTML和其他可预览类型
               contentType.equals("text/html") ||
               contentType.equals("application/xhtml+xml") ||
               // 添加更多可预览的文本类型
               contentType.equals("application/xml") ||
               contentType.equals("application/json") ||
               contentType.equals("application/javascript") ||
               contentType.equals("application/x-javascript") ||
               // 添加Office文档类型
               contentType.equals("application/msword") ||
               contentType.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document") ||
               contentType.equals("application/vnd.ms-excel") ||
               contentType.equals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet") ||
               contentType.equals("application/vnd.ms-powerpoint") ||
               contentType.equals("application/vnd.openxmlformats-officedocument.presentationml.presentation");
    }
    
    @Override
    public Map<String, Object> getPreviewData(String fileId) throws IOException {
        FileInfo fileInfo = getFileInfo(fileId);
        if (fileInfo == null) {
            throw new FileNotFoundException("File not found with ID: " + fileId);
        }
        
        File file = new File(fileInfo.getPath());
        if (!file.exists()) {
            throw new FileNotFoundException("File not found at path: " + fileInfo.getPath());
        }
        
        Map<String, Object> previewData = new HashMap<>();
        previewData.put("fileId", fileId);
        previewData.put("fileName", fileInfo.getFileName());
        previewData.put("contentType", fileInfo.getContentType());
        previewData.put("size", fileInfo.getSize());
        previewData.put("formattedSize", fileInfo.getFormattedSize());
        
        String contentType = fileInfo.getContentType();
        if (contentType != null) {
            if (contentType.startsWith("image/")) {
                previewData.put("previewType", "image");
            } else if (contentType.startsWith("video/")) {
                previewData.put("previewType", "video");
            } else if (contentType.startsWith("audio/")) {
                previewData.put("previewType", "audio");
            } else if (contentType.equals("application/pdf")) {
                previewData.put("previewType", "pdf");
            } else if (contentType.equals("text/html") || contentType.equals("application/xhtml+xml")) {
                // 对于HTML文件，提供iframe预览
                previewData.put("previewType", "html");
            } else if (contentType.startsWith("text/") || 
                       contentType.equals("application/xml") || 
                       contentType.equals("application/json")) {
                // 对于文本文件，读取部分内容
                previewData.put("previewType", "text");
                
                // 限制大小，最多读取前100KB
                long maxSize = Math.min(file.length(), 100 * 1024);
                byte[] buffer = new byte[(int) maxSize];
                
                try (FileInputStream fis = new FileInputStream(file)) {
                    fis.read(buffer);
                }
                
                // 将内容转换为字符串（尝试检测编码）
                String encoding = detectEncoding(buffer);
                String content = new String(buffer, encoding);
                previewData.put("content", content);
                previewData.put("encoding", encoding);
            } else if (contentType.equals("application/vnd.android.package-archive")) {
                // APK文件
                previewData.put("previewType", "apk");
                // 这里可以添加APK文件的额外信息，如图标、版本等
                previewData.put("icon", "apk-icon"); // 需要实际实现提取APK图标
            } else {
                previewData.put("previewType", "none");
            }
        } else {
            previewData.put("previewType", "none");
        }
        
        return previewData;
    }
    
    /**
     * 检测文本文件编码
     */
    private String detectEncoding(byte[] buffer) {
        // 简单检测UTF-8 BOM
        if (buffer.length >= 3 && 
            buffer[0] == (byte) 0xEF && 
            buffer[1] == (byte) 0xBB && 
            buffer[2] == (byte) 0xBF) {
            return "UTF-8";
        }
        
        // 简单检测UTF-16 BOM
        if (buffer.length >= 2) {
            if (buffer[0] == (byte) 0xFE && buffer[1] == (byte) 0xFF) {
                return "UTF-16BE";
            }
            if (buffer[0] == (byte) 0xFF && buffer[1] == (byte) 0xFE) {
                return "UTF-16LE";
            }
        }
        
        // 尝试检测UTF-8特征
        boolean isUtf8 = true;
        int i = 0;
        while (i < buffer.length) {
            if ((buffer[i] & 0x80) == 0) { // 0xxxxxxx ASCII
                i++;
            } else if ((buffer[i] & 0xE0) == 0xC0) { // 110xxxxx 10xxxxxx
                if (i + 1 >= buffer.length || (buffer[i + 1] & 0xC0) != 0x80) {
                    isUtf8 = false;
                    break;
                }
                i += 2;
            } else if ((buffer[i] & 0xF0) == 0xE0) { // 1110xxxx 10xxxxxx 10xxxxxx
                if (i + 2 >= buffer.length || 
                    (buffer[i + 1] & 0xC0) != 0x80 || 
                    (buffer[i + 2] & 0xC0) != 0x80) {
                    isUtf8 = false;
                    break;
                }
                i += 3;
            } else if ((buffer[i] & 0xF8) == 0xF0) { // 11110xxx 10xxxxxx 10xxxxxx 10xxxxxx
                if (i + 3 >= buffer.length || 
                    (buffer[i + 1] & 0xC0) != 0x80 || 
                    (buffer[i + 2] & 0xC0) != 0x80 ||
                    (buffer[i + 3] & 0xC0) != 0x80) {
                    isUtf8 = false;
                    break;
                }
                i += 4;
            } else {
                isUtf8 = false;
                break;
            }
        }
        
        return isUtf8 ? "UTF-8" : "GBK"; // 如果不是UTF-8则假设为GBK（中文常用编码）
    }
    
    @Override
    public List<String> getSupportedFileTypes() {
        return supportedTypes;
    }
    
    @Override
    public boolean isFileTypeSupported(String contentType) {
        if (contentType == null) {
            logger.debug("文件类型为null，不支持");
            return false;
        }
        
        logger.debug("检查文件类型是否支持: {}, 支持的类型: {}", contentType, supportedTypes);
        
        // 如果设置了"*/*"，表示支持所有文件类型
        if (supportedTypes.contains("*/*")) {
            logger.debug("支持所有文件类型 (*/*), 允许: {}", contentType);
            return true;
        }
        
        // 直接匹配完整的MIME类型
        if (supportedTypes.contains(contentType)) {
            logger.debug("直接匹配MIME类型成功: {}", contentType);
            return true;
        }
        
        // 处理通配符匹配，如 "image/*"
        for (String supportedType : supportedTypes) {
            if (supportedType.endsWith("/*")) {
                String prefix = supportedType.substring(0, supportedType.length() - 2);
                if (contentType.startsWith(prefix + "/")) {
                    logger.debug("通配符匹配成功: {} 匹配 {}", contentType, supportedType);
                    return true;
                }
            }
        }
        
        logger.debug("文件类型不支持: {}", contentType);
        return false;
    }

    @Override
    public String getUploadDirectory() {
        return this.uploadDirectory;
    }
    
    @Override
    public void reloadFileInfo() {
        logger.info("重新加载文件信息");
        fileInfoMap.clear();
        List<FileInfo> loadedFiles = loadFileInfo();
        for (FileInfo fileInfo : loadedFiles) {
            fileInfoMap.put(fileInfo.getId(), fileInfo);
        }
        logger.info("文件信息重新加载完成，共 {} 个文件", loadedFiles.size());
    }
    
    @Override
    public void updateFileList(List<FileInfo> files) {
        logger.info("更新文件列表，文件数量: {}", files.size());
        fileInfoMap.clear();
        for (FileInfo fileInfo : files) {
            fileInfoMap.put(fileInfo.getId(), fileInfo);
        }
        saveFileInfo(files);
        logger.info("文件列表更新完成");
    }
    
    @Override
    public boolean resetFileInfo() {
        try {
            logger.info("重置文件信息");
            
            // 清空文件信息映射
            fileInfoMap.clear();
            
            // 删除旧的文件信息文件
            if (fileInfoFile != null) {
                File file = new File(fileInfoFile);
                if (file.exists()) {
                    boolean deleted = file.delete();
                    logger.info("删除旧的文件信息文件: {} - {}", fileInfoFile, deleted ? "成功" : "失败");
                }
            }
            
            // 重新创建文件信息文件
            saveFileInfo(new ArrayList<>());
            
            // 扫描上传目录
            if (uploadDirectory != null) {
                File uploadDir = new File(uploadDirectory);
                if (uploadDir.exists() && uploadDir.isDirectory()) {
                    logger.info("扫描上传目录: {}", uploadDirectory);
                    
                    // 递归扫描目录
                    scanDirectory(uploadDir, null);
                    
                    // 保存文件信息
                    saveFileInfo(new ArrayList<>(fileInfoMap.values()));
                    
                    logger.info("文件信息重置完成，共发现 {} 个文件", fileInfoMap.size());
                    return true;
                } else {
                    logger.warn("上传目录不存在或不是目录: {}", uploadDirectory);
                }
            } else {
                logger.warn("上传目录未设置");
            }
            
            return false;
        } catch (Exception e) {
            logger.error("重置文件信息失败: {}", e.getMessage(), e);
            return false;
        }
    }
    
    // 递归扫描目录
    private void scanDirectory(File directory, String userId) {
        if (!directory.exists() || !directory.isDirectory()) {
            return;
        }
        
        File[] files = directory.listFiles();
        if (files == null) {
            return;
        }
        
        for (File file : files) {
            if (file.isDirectory()) {
                // 递归扫描子目录
                scanDirectory(file, userId);
            } else if (file.isFile() && !file.getName().equals("fileInfo.json")) {
                // 为文件创建FileInfo对象
                String fileId = UUID.randomUUID().toString();
                FileInfo fileInfo = new FileInfo();
                fileInfo.setId(fileId);
                fileInfo.setFileName(file.getName());
                fileInfo.setOriginalFileName(file.getName());
                fileInfo.setPath(file.getAbsolutePath());
                fileInfo.setSize(file.length());
                fileInfo.setUploadDate(new Date(file.lastModified()));
                fileInfo.setStatus("UPLOADED");
                
                // 尝试确定文件类型
                String contentType = FileUtils.getContentTypeByFileName(file.getName());
                fileInfo.setContentType(contentType);
                
                // 设置上传者
                if (userId != null) {
                    fileInfo.setUploadedBy(userId);
                }
                
                // 添加到文件信息映射
                fileInfoMap.put(fileId, fileInfo);
                
                logger.debug("发现文件: id={}, name={}, path={}", fileId, file.getName(), file.getAbsolutePath());
            }
        }
    }
}