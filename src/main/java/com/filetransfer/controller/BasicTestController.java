package com.filetransfer.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * 最基本的测试控制器
 */
@Controller
public class BasicTestController {
    
    @GetMapping("/basic-test")
    @ResponseBody
    public String basicTest() {
        return "基本测试成功!";
    }
    
    @GetMapping("/api/basic-test")
    @ResponseBody
    public String apiBasicTest() {
        return "API基本测试成功!";
    }
} 