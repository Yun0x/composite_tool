package com.tool.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Controller
public class PageController {

    @GetMapping("/")
    public String home() {
        return "redirect:/index.html";
    }

}
