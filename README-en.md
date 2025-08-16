# File Transfer System (file-transfer) User Guide

## 1. Project Overview

This project is a file transfer and management system based on Spring MVC, supporting user authentication, file upload/download, WebSocket real-time communication, etc. It is suitable for file sharing and management in LAN or Internet environments.

---

## 2. Technology Stack & Dependencies

- Java 11
- Spring Framework 5.3.29 (spring-webmvc, spring-context, spring-websocket, spring-messaging)
- Servlet API 4.0.1
- Jackson 2.15.2 (JSON processing)
- Apache Commons FileUpload 1.5 & Commons IO 2.13.0 (file upload/IO)
- SLF4J 2.0.7 + Logback 1.4.11 (logging)
- JJWT 0.11.5 (JWT token authentication)
- Maven build tool, packaged as WAR for deployment

---

## 3. Installation & Deployment

### 1. Prerequisites

- JDK 11 or above
- Maven 3.6 or above
- Servlet 4.0+ compatible web container (e.g., Tomcat 9+)

### 2. Clone the Project

```bash
git clone 
cd file_transfer
```

### 3. Build the Project

```bash
mvn clean package
```

- The generated WAR package will be in `target/file-transfer.war`

### 4. Deploy to Tomcat

- Copy `target/file-transfer.war` to Tomcat's `webapps` directory
- Start Tomcat
- Visit `http://localhost:8080/file-transfer/`

### 5. Configuration

- Configuration files are located at `src/main/resources/application.properties` and `WEB-INF/applicationContext.xml`
- You can modify database, file storage path, and other parameters as needed

---

## 4. Common Commands

- Build: `mvn clean package`
- Run tests: `mvn test`
- Clean: `mvn clean`

---

## 5. Directory Structure

- `src/main/java/com/filetransfer/` Main Java code
- `src/main/resources/` Configuration files
- `src/main/webapp/` Frontend pages and static resources
- `pom.xml` Maven configuration

---

## 6. Contact

For any questions, please contact the project maintainer or submit an issue. 