package com.filetransfer.filter;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Filter for handling CORS requests
 */
public class CORSFilter implements Filter {
    
    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
    }
    
    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;
        
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Origin, X-Requested-With, Content-Type, Accept, Authorization");
        response.setHeader("Access-Control-Max-Age", "3600");
        
        // 允许在iframe中加载内容
        response.setHeader("X-Frame-Options", "SAMEORIGIN");
        
        // 对于预览请求，允许在任何地方嵌入
        if (request.getRequestURI().contains("/direct-preview")) {
            response.setHeader("X-Frame-Options", "ALLOWALL");
            response.setHeader("Content-Security-Policy", "frame-ancestors *");
        }
        
        // For preflight requests
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_OK);
        } else {
            // For JSON API endpoints, set the content type to application/json
            if (request.getRequestURI().contains("/api/") && 
                !request.getRequestURI().endsWith(".html") && 
                !request.getRequestURI().endsWith(".css") && 
                !request.getRequestURI().endsWith(".js") &&
                !request.getRequestURI().contains("/direct-preview")) {
                response.setContentType("application/json");
            }
            chain.doFilter(req, res);
        }
    }
    
    @Override
    public void destroy() {
    }
} 