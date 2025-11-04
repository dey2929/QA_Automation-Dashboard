package com.qa.dashboard.controller;

import com.qa.dashboard.entity.Build;
import com.qa.dashboard.service.DashboardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
@CrossOrigin(origins = "*")
public class DashboardController {
    
    @Autowired
    private DashboardService dashboardService;
    
    @GetMapping("/builds/{suiteName}")
    public ResponseEntity<List<Build>> getBuildsBySuite(@PathVariable String suiteName) {
        List<Build> builds = dashboardService.getRecentBuilds(suiteName, 10);
        return ResponseEntity.ok(builds);
    }
    
    @GetMapping("/latest-build/{suiteName}")
    public ResponseEntity<Build> getLatestBuild(@PathVariable String suiteName) {
        Build latestBuild = dashboardService.getLatestBuild(suiteName);
        return ResponseEntity.ok(latestBuild);
    }
    
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP", "message", "Dashboard API is running"));
    }
    
    @GetMapping("/flaky-tests")
    public ResponseEntity<List<Map<String, Object>>> getFlakyTests(
            @RequestParam(required = false, defaultValue = "Search_TestSuite") String suiteName) {
        List<Map<String, Object>> flakyTests = dashboardService.getFlakyTests(suiteName, 10);
        return ResponseEntity.ok(flakyTests);
    }
    
    @GetMapping("/slowest-tests")
    public ResponseEntity<List<Map<String, Object>>> getSlowestTests(
            @RequestParam(required = false, defaultValue = "Search_TestSuite") String suiteName) {
        List<Map<String, Object>> slowTests = dashboardService.getSlowestTests(suiteName, 10);
        return ResponseEntity.ok(slowTests);
    }
    
    @GetMapping("/failures-by-feature")
    public ResponseEntity<Map<String, Long>> getFailuresByFeature(
            @RequestParam(required = false, defaultValue = "Search_TestSuite") String suiteName) {
        Map<String, Long> failures = dashboardService.getFailuresByFeature(suiteName);
        return ResponseEntity.ok(failures);
    }
    
    @GetMapping("/build-duration-stats")
    public ResponseEntity<Map<String, Object>> getBuildDurationStats(
            @RequestParam(required = false, defaultValue = "Search_TestSuite") String suiteName) {
        Map<String, Object> stats = dashboardService.getBuildDurationStats(suiteName);
        return ResponseEntity.ok(stats);
    }
}