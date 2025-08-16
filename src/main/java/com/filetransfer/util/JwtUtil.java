package com.filetransfer.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Utility class for JWT token operations
 */
public class JwtUtil {
    
    private static final Logger logger = LoggerFactory.getLogger(JwtUtil.class);
    
    // 默认值，以防属性注入失败
    private String secret = "defaultSecretKeyThatIsLongEnoughForHmacSHA256Algorithm";
    private long expiration = 86400000; // 24小时
    
    private Key key;
    
    public JwtUtil() {
        logger.info("JwtUtil constructor called");
        // 在构造函数中初始化key，确保即使属性注入失败也有默认值
        initKey();
    }
    
    /**
     * 初始化JWT密钥
     */
    private void initKey() {
        try {
            // 确保密钥长度足够（至少256位）
            if (secret == null || secret.length() < 32) {
                logger.warn("JWT secret is too short or null, generating secure key");
                this.key = Keys.secretKeyFor(SignatureAlgorithm.HS256);
            } else {
                this.key = Keys.hmacShaKeyFor(secret.getBytes());
                logger.info("JWT key generated from secret");
            }
        } catch (Exception e) {
            logger.error("Error generating JWT key", e);
            // 使用预定义的安全密钥作为最后的备选方案
            this.key = Keys.secretKeyFor(SignatureAlgorithm.HS256);
            logger.info("Generated fallback secret key");
        }
    }
    
    public void init() {
        logger.info("JwtUtil initialized with secret: {}, expiration: {}", 
                secret != null ? (secret.substring(0, 3) + "...") : "null", 
                expiration);
        
        if (key == null) {
            logger.warn("Key is null after initialization, generating default key");
            this.key = Keys.secretKeyFor(SignatureAlgorithm.HS256);
        }
    }
    
    public void setSecret(String secret) {
        logger.info("Setting JWT secret: {}", secret != null ? (secret.substring(0, 3) + "...") : "null");
        this.secret = secret;
        // 重新初始化密钥
        initKey();
    }
    
    public void setExpiration(long expiration) {
        logger.info("Setting JWT expiration: {}", expiration);
        this.expiration = expiration;
    }
    
    /**
     * Generate a JWT token for a user
     * 
     * @param userId The ID of the user
     * @return The JWT token
     */
    public String generateToken(String userId) {
        if (key == null) {
            logger.error("Cannot generate token: key is null");
            throw new IllegalStateException("JWT key is not initialized");
        }
        
        Map<String, Object> claims = new HashMap<>();
        return createToken(claims, userId);
    }
    
    /**
     * Create a JWT token
     * 
     * @param claims The claims to include in the token
     * @param subject The subject of the token
     * @return The JWT token
     */
    private String createToken(Map<String, Object> claims, String subject) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);
        
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }
    
    /**
     * Validate a JWT token
     * 
     * @param token The JWT token
     * @return True if the token is valid, false otherwise
     */
    public boolean validateToken(String token) {
        if (key == null) {
            logger.error("Cannot validate token: key is null");
            return false;
        }
        
        try {
            Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token);
            return true;
        } catch (Exception e) {
            logger.warn("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Extract the user ID from a JWT token
     * 
     * @param token The JWT token
     * @return The user ID
     */
    public String getUserIdFromToken(String token) {
        if (key == null) {
            logger.error("Cannot extract user ID: key is null");
            return null;
        }
        
        try {
            logger.debug("尝试从token解析用户ID: {}", token.substring(0, Math.min(10, token.length())) + "...");
            String userId = extractSubject(token);
            logger.debug("成功从token解析用户ID: {}", userId);
            return userId;
        } catch (Exception e) {
            logger.warn("从token解析用户ID失败: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Extract the subject from a JWT token
     * 
     * @param token The JWT token
     * @return The subject
     */
    private String extractSubject(String token) {
        return extractClaim(token, Claims::getSubject);
    }
    
    /**
     * Extract a claim from a JWT token
     * 
     * @param token The JWT token
     * @param claimsResolver The function to extract the claim
     * @return The extracted claim
     */
    private <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }
    
    /**
     * Extract all claims from a JWT token
     * 
     * @param token The JWT token
     * @return The claims
     */
    private Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
} 