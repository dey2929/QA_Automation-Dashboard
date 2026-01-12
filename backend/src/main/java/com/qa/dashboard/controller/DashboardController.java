package com.qa.dashboard.controller;

import com.qa.dashboard.entity.Build;
import com.qa.dashboard.service.DashboardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
@CrossOrigin(origins = "*")
public class DashboardController {
    
    @Autowired
    private DashboardService dashboardService;
    
    @GetMapping("/builds/{suiteName}")
    public ResponseEntity<List<Build>> getBuildsBySuite(
            @PathVariable String suiteName,
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate,
            @RequestParam(required = false, defaultValue = "10") int limit) {
        LocalDateTime from = parseDate(fromDate);
        LocalDateTime to = parseDateToEndOfDay(toDate);
        List<Build> builds = dashboardService.getRecentBuilds(suiteName, limit, from, to);
        return ResponseEntity.ok(builds);
    }
    
    @GetMapping("/builds/{suiteName}/all")
    public ResponseEntity<List<Build>> getAllBuildsBySuite(
            @PathVariable String suiteName,
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate) {
        LocalDateTime from = parseDate(fromDate);
        LocalDateTime to = parseDateToEndOfDay(toDate);
        List<Build> builds = dashboardService.getAllBuilds(suiteName, from, to);
        return ResponseEntity.ok(builds);
    }
    
    @GetMapping("/latest-build/{suiteName}")
    public ResponseEntity<Build> getLatestBuild(
            @PathVariable String suiteName,
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate) {
        LocalDateTime from = parseDate(fromDate);
        LocalDateTime to = parseDateToEndOfDay(toDate);
        Build latestBuild = dashboardService.getLatestBuild(suiteName, from, to);
        return ResponseEntity.ok(latestBuild);
    }
    
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP", "message", "Dashboard API is running"));
    }
    
    @GetMapping("/flaky-tests")
    public ResponseEntity<List<Map<String, Object>>> getFlakyTests(
            @RequestParam(required = false, defaultValue = "Search_TestSuite") String suiteName,
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate) {
        LocalDateTime from = parseDate(fromDate);
        LocalDateTime to = parseDateToEndOfDay(toDate);
        List<Map<String, Object>> flakyTests = dashboardService.getFlakyTests(suiteName, 10, from, to);
        return ResponseEntity.ok(flakyTests);
    }
    
    @GetMapping("/slowest-tests")
    public ResponseEntity<List<Map<String, Object>>> getSlowestTests(
            @RequestParam(required = false, defaultValue = "Search_TestSuite") String suiteName,
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate) {
        LocalDateTime from = parseDate(fromDate);
        LocalDateTime to = parseDateToEndOfDay(toDate);
        List<Map<String, Object>> slowTests = dashboardService.getSlowestTests(suiteName, 10, from, to);
        return ResponseEntity.ok(slowTests);
    }
    
    @GetMapping("/failures-by-feature")
    public ResponseEntity<Map<String, Long>> getFailuresByFeature(
            @RequestParam(required = false, defaultValue = "Search_TestSuite") String suiteName,
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate) {
        LocalDateTime from = parseDate(fromDate);
        LocalDateTime to = parseDateToEndOfDay(toDate);
        Map<String, Long> failures = dashboardService.getFailuresByFeature(suiteName, from, to);
        return ResponseEntity.ok(failures);
    }
    
    @GetMapping("/build-duration-stats")
    public ResponseEntity<Map<String, Object>> getBuildDurationStats(
            @RequestParam(required = false, defaultValue = "Search_TestSuite") String suiteName,
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate) {
        LocalDateTime from = parseDate(fromDate);
        LocalDateTime to = parseDateToEndOfDay(toDate);
        Map<String, Object> stats = dashboardService.getBuildDurationStats(suiteName, from, to);
        return ResponseEntity.ok(stats);
    }
    
    private LocalDateTime parseDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) {
            return null;
        }
        try {
            // Parse ISO date string (YYYY-MM-DD)
            LocalDate date = LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE);
            return date.atStartOfDay();
        } catch (Exception e) {
            return null;
        }
    }
    
    private LocalDateTime parseDateToEndOfDay(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) {
            return null;
        }
        try {
            // Parse ISO date string (YYYY-MM-DD) and set to end of day
            LocalDate date = LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE);
            return date.atTime(23, 59, 59, 999999999);
        } catch (Exception e) {
            return null;
        }
    }
}