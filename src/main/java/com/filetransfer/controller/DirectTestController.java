package com.filetransfer.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * 直接测试控制器，尽可能简单
 */
@Controller
public class DirectTestController {

    @GetMapping("/direct-test")
    @ResponseBody
    public String directTest() {
        return "直接测试成功!";
    }
} 