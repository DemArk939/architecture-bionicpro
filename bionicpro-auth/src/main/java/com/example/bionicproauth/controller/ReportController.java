package com.example.bionicproauth.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ReportController {

    @GetMapping("/report")
    public ResponseEntity<Map<String, String>> getReport() {
        // В реальном приложении здесь можно проксировать запрос к API с access token'ом
        Map<String, String> report = new HashMap<>();
        report.put("content", "This is a sample usage report.");
        report.put("generated", java.time.Instant.now().toString());
        return ResponseEntity.ok(report);
    }
}