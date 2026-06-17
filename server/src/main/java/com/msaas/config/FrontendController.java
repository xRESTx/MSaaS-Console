package com.msaas.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class FrontendController {

    @GetMapping({
            "/",
            "/login",
            "/register",
            "/console",
            "/console/**"
    })
    public String frontend() {
        return "forward:/index.html";
    }
}
