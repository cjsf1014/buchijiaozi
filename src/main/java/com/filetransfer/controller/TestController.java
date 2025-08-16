package com.filetransfer.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test controller to diagnose routing issues
 */
@RestController
public class TestController {
    
    private static final Logger logger = LoggerFactory.getLogger(TestController.class);
    
    @GetMapping("/test")
    public String test() {
        logger.info("Root test endpoint reached");
        return "Test controller is working!";
    }
    
    @GetMapping("/api/test")
    public String apiTest() {
        logger.info("API test endpoint reached");
        return "API test endpoint is working!";
    }
} 