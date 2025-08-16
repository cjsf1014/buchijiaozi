package com.filetransfer.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 简单测试控制器，用于诊断路由问题
 */
@RestController
public class SimpleTestController {
    
    private static final Logger logger = LoggerFactory.getLogger(SimpleTestController.class);
    
    @GetMapping("/simple-test")
    public String simpleTest() {
        logger.info("简单测试端点被访问");
        return "简单测试控制器工作正常!";
    }
    
    @GetMapping("/api/simple-test")
    public String apiSimpleTest() {
        logger.info("API简单测试端点被访问");
        return "API简单测试端点工作正常!";
    }
    
    @PostMapping("/api/echo")
    public Map<String, Object> echo(@RequestBody Map<String, Object> payload) {
        logger.info("收到echo请求: {}", payload);
        return payload;
    }
} 