package com.tool.controller;

import com.tool.service.TestService;
import com.tool.util.Result;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController
public class PageController {

    @Resource
    private TestService testService;

    @GetMapping("/")
    public String home() {
        return "redirect:/index.html";
    }

}
