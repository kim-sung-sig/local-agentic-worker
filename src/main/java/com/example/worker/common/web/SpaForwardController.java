package com.example.worker.common.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaForwardController {

    // /api/** 와 정적 파일을 제외한 모든 경로를 index.html로 포워딩 (Vue Router Hash mode이므로 실제론 불필요하지만 안전망)
    @GetMapping(value = { "/", "/projects/**", "/issues/**" })
    public String forward() {
        return "forward:/index.html";
    }
}
