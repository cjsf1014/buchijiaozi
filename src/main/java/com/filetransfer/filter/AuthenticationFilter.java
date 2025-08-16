package com.filetransfer.filter;

import com.filetransfer.util.JwtUtil;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.SignatureAlgorithm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

/**
 * Filter for JWT authentication
 */
public class AuthenticationFilter implements Filter {
    
    private static final Logger logger = LoggerFactory.getLogger(AuthenticationFilter.class);
    
    private JwtUtil jwtUtil;
    
    // Paths that don't require authentication
    private static final List<String> PUBLIC_PATHS = Arrays.asList(
            "/api/users/register",
            "/api/users/login",
            "/api/files/public",
            "/api/transfers/access",
            "/api/simple-test",
            "/api/echo",
            "/api/test",
            "/api/users/health",
            "/simple-test",
            "/test",
            "/basic-test",
            "/api/basic-test",
            "/debug/users",
            "/debug/reset-password",
            "/debug/test-register",
            "/direct-test"
    );
    
    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        logger.info("Initializing AuthenticationFilter");
        
        try {
            // 手动创建JwtUtil实例
            jwtUtil = new JwtUtil();
            
            // 尝试从application.properties加载配置
            Properties props = new Properties();
            try {
                props = PropertiesLoaderUtils.loadProperties(new ClassPathResource("application.properties"));
                String secret = props.getProperty("jwt.secret", "defaultSecretKeyThatIsLongEnoughForHmacSHA256Algorithm");
                String expirationStr = props.getProperty("jwt.expiration", "86400000");
                long expiration = Long.parseLong(expirationStr);
                
                jwtUtil.setSecret(secret);
                jwtUtil.setExpiration(expiration);
                
                logger.info("Loaded JWT properties from application.properties");
            } catch (Exception e) {
                logger.warn("Failed to load application.properties, using default values", e);
                // 使用默认值
                jwtUtil.setSecret("defaultSecretKeyThatIsLongEnoughForHmacSHA256Algorithm");
                jwtUtil.setExpiration(86400000L); // 24小时
            }
            
            // 手动初始化JwtUtil
            jwtUtil.init();
            
            logger.info("AuthenticationFilter initialized successfully with JwtUtil");
        } catch (Exception e) {
            logger.error("Failed to initialize AuthenticationFilter", e);
            throw new ServletException("Failed to initialize AuthenticationFilter", e);
        }
    }
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        // 检查JwtUtil是否已初始化
        if (jwtUtil == null) {
            logger.error("JwtUtil is null in AuthenticationFilter.doFilter");
            HttpServletResponse httpResponse = (HttpServletResponse) response;
            httpResponse.setContentType("application/json;charset=UTF-8");
            httpResponse.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            httpResponse.getWriter().write("{\"success\":false,\"message\":\"Server configuration error: JwtUtil not initialized\"}");
            return;
        }
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        
        // 设置响应头，确保返回的是JSON格式
        httpResponse.setContentType("application/json;charset=UTF-8");
        
        String requestURI = httpRequest.getRequestURI();
        String contextPath = httpRequest.getContextPath();
        
        // 记录请求信息，帮助调试
        logger.debug("Request URI: {}, Method: {}", requestURI, httpRequest.getMethod());
        
        // 移除上下文路径前缀，以便与PUBLIC_PATHS匹配
        if (contextPath != null && !contextPath.isEmpty() && requestURI.startsWith(contextPath)) {
            requestURI = requestURI.substring(contextPath.length());
            logger.debug("Adjusted request URI: {}", requestURI);
        }
        
        // Check if path is public
        boolean isPublicPath = false;
        for (String path : PUBLIC_PATHS) {
            if (requestURI.startsWith(path)) {
                isPublicPath = true;
                logger.debug("Public path matched: {}", path);
                break;
            }
        }
        
        // 处理文件下载请求中的token参数
        if (requestURI.startsWith("/api/files/download/") && !isPublicPath) {
            String token = httpRequest.getParameter("token");
            if (token != null && !token.isEmpty()) {
                logger.debug("Token found in query parameter for download request");
                try {
                    if (jwtUtil.validateToken(token)) {
                        chain.doFilter(request, response);
                        return;
                    }
                } catch (Exception e) {
                    logger.warn("Invalid token in query parameter: {}", e.getMessage());
                }
            }
        }
        
        if (isPublicPath) {
            logger.debug("Allowing access to public path");
            chain.doFilter(request, response);
            return;
        }
        
        // Check for token
        String authHeader = httpRequest.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            logger.warn("Missing or invalid Authorization header");
            httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            httpResponse.getWriter().write("{\"success\":false,\"message\":\"Unauthorized\"}");
            return;
        }
        
        String token = authHeader.substring(7);
        
        try {
            if (!jwtUtil.validateToken(token)) {
                logger.warn("Invalid token");
                httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                httpResponse.getWriter().write("{\"success\":false,\"message\":\"Invalid token\"}");
                return;
            }
            
            logger.debug("Token validated successfully");
            chain.doFilter(request, response);
        } catch (Exception e) {
            logger.error("Error validating token", e);
            httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            httpResponse.getWriter().write("{\"success\":false,\"message\":\"" + e.getMessage() + "\"}");
        }
    }
    
    @Override
    public void destroy() {
    }
} 