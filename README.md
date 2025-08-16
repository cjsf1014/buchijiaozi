# 文件传输系统（file-transfer）使用说明

## 一、项目简介

本项目是一个基于 Spring MVC 的文件传输与管理系统，支持用户认证、文件上传下载、WebSocket 实时通信等功能。适用于局域网或互联网环境下的文件共享与管理。

---

## 二、技术架构与依赖

- Java 11
- Spring Framework 5.3.29（spring-webmvc, spring-context, spring-websocket, spring-messaging）
- Servlet API 4.0.1
- Jackson 2.15.2（JSON 处理）
- Apache Commons FileUpload 1.5 & Commons IO 2.13.0（文件上传/IO）
- SLF4J 2.0.7 + Logback 1.4.11（日志）
- JJWT 0.11.5（JWT 令牌认证）
- Maven 构建工具，打包为 WAR 部署

---

## 三、安装与部署

### 1. 环境准备

- JDK 11 及以上
- Maven 3.6 及以上
- 支持 Servlet 4.0+ 的 Web 容器（如 Tomcat 9+）

### 2. 克隆项目

```bash
git clone
cd file_transfer
```

### 3. 构建项目

```bash
mvn clean package
```

- 生成的 WAR 包位于 `target/file-transfer.war`

### 4. 部署到 Tomcat

- 将 `target/file-transfer.war` 复制到 Tomcat 的 `webapps` 目录
- 启动 Tomcat
- 访问 `http://localhost:8080/file-transfer/`

### 5. 配置文件

- 配置文件位于 `src/main/resources/application.properties` 和 `WEB-INF/applicationContext.xml`
- 可根据实际需求修改数据库、文件存储路径等参数

---

## 四、常用命令

- 构建项目：`mvn clean package`
- 运行测试（如有）：`mvn test`
- 清理：`mvn clean`

---

## 五、目录结构

- `src/main/java/com/filetransfer/` 主要 Java 代码
- `src/main/resources/` 配置文件
- `src/main/webapp/` 前端页面与静态资源
- `pom.xml` Maven 配置

---

## 六、联系方式

如有问题请联系项目维护者，或提交 issue。 
