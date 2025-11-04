package com.qa.dashboard.controller;

import com.qa.dashboard.service.JenkinsReportFetcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@CrossOrigin(origins = "*")
public class AdminController {
    
    @Autowired
    private JenkinsReportFetcher reportFetcher;
    
    @PostMapping("/sync-reports")
    public ResponseEntity<Map<String, Object>> syncReports() {
        try {
            System.out.println("Manual report sync triggered via API");
            reportFetcher.fetchReports();
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "Reports synced successfully from Jenkins");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", e.getMessage());
            
            return ResponseEntity.status(500).body(response);
        }
    }
}

