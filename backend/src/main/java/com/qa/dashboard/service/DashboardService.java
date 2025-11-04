package com.qa.dashboard.service;

import com.qa.dashboard.entity.Build;
import com.qa.dashboard.entity.TestResult;
import com.qa.dashboard.entity.TestStatus;
import com.qa.dashboard.repository.BuildRepository;
import com.qa.dashboard.repository.TestResultRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class DashboardService {
    
    @Autowired
    private BuildRepository buildRepository;
    
    @Autowired
    private TestResultRepository testResultRepository;
    
    public List<Build> getRecentBuilds(String suiteName, int limit) {
        return buildRepository.findBySuiteNameOrderByTimestampDesc(suiteName, 
            PageRequest.of(0, limit));
    }
    
    public Build getLatestBuild(String suiteName) {
        return buildRepository.findFirstBySuiteNameOrderByTimestampDesc(suiteName);
    }
    
    public List<Map<String, Object>> getFlakyTests(String suiteName, int limit) {
        // Get builds for the suite first
        List<String> buildIds = buildRepository.findAll()
            .stream()
            .filter(b -> suiteName == null || suiteName.equals(b.getSuiteName()))
            .map(Build::getBuildId)
            .collect(Collectors.toList());
        
        // Get all test results for these builds
        Map<String, List<TestResult>> testsByName = testResultRepository.findAll()
            .stream()
            .filter(t -> suiteName == null || buildIds.contains(t.getBuildId()))
            .collect(Collectors.groupingBy(TestResult::getTestName));
        
        // Calculate flakiness for each test
        List<Map<String, Object>> flakyTests = new ArrayList<>();
        
        for (Map.Entry<String, List<TestResult>> entry : testsByName.entrySet()) {
            String testName = entry.getKey();
            List<TestResult> results = entry.getValue();
            
            long totalRuns = results.size();
            long failures = results.stream().filter(r -> r.getStatus() == TestStatus.FAIL).count();
            
            if (totalRuns >= 2 && failures > 0) {
                double failureRate = (double) failures / totalRuns * 100;
                
                Map<String, Object> testInfo = new HashMap<>();
                testInfo.put("testName", testName);
                testInfo.put("totalRuns", totalRuns);
                testInfo.put("failures", failures);
                testInfo.put("failureRate", Math.round(failureRate * 10.0) / 10.0);
                testInfo.put("feature", results.get(0).getFeature());
                
                flakyTests.add(testInfo);
            }
        }
        
        // Sort by failure rate descending
        flakyTests.sort((a, b) -> {
            Double rateA = (Double) a.get("failureRate");
            Double rateB = (Double) b.get("failureRate");
            return rateB.compareTo(rateA);
        });
        
        return flakyTests.stream().limit(limit).collect(Collectors.toList());
    }
    
    public List<Map<String, Object>> getSlowestTests(String suiteName, int limit) {
        // Get builds for the suite first
        List<String> buildIds = buildRepository.findAll()
            .stream()
            .filter(b -> suiteName == null || suiteName.equals(b.getSuiteName()))
            .map(Build::getBuildId)
            .collect(Collectors.toList());
        
        // Get all test results for these builds
        Map<String, List<TestResult>> testsByName = testResultRepository.findAll()
            .stream()
            .filter(t -> (suiteName == null || buildIds.contains(t.getBuildId())) 
                    && t.getExecutionTime() != null && t.getExecutionTime() > 0)
            .collect(Collectors.groupingBy(TestResult::getTestName));
        
        List<Map<String, Object>> slowestTests = new ArrayList<>();
        
        for (Map.Entry<String, List<TestResult>> entry : testsByName.entrySet()) {
            String testName = entry.getKey();
            List<TestResult> results = entry.getValue();
            
            if (results.size() == 0) continue;
            
            // Calculate median, min, max
            List<Integer> times = results.stream()
                .map(TestResult::getExecutionTime)
                .sorted()
                .collect(Collectors.toList());
            
            int median = calculateMedian(times);
            int min = times.get(0);
            int max = times.get(times.size() - 1);
            
            Map<String, Object> testInfo = new HashMap<>();
            testInfo.put("testName", testName);
            testInfo.put("medianTime", median);
            testInfo.put("minTime", min);
            testInfo.put("maxTime", max);
            testInfo.put("runs", results.size());
            testInfo.put("feature", results.get(0).getFeature());
            
            slowestTests.add(testInfo);
        }
        
        // Sort by median time descending
        slowestTests.sort((a, b) -> Integer.compare(
            (Integer)b.get("medianTime"), 
            (Integer)a.get("medianTime")
        ));
        
        return slowestTests.stream().limit(limit).collect(Collectors.toList());
    }
    
    private int calculateMedian(List<Integer> values) {
        if (values.isEmpty()) return 0;
        
        int size = values.size();
        if (size % 2 == 0) {
            return (values.get(size/2 - 1) + values.get(size/2)) / 2;
        } else {
            return values.get(size/2);
        }
    }
    
    public Map<String, Long> getFailuresByFeature(String suiteName) {
        // Get builds for the suite first
        List<String> buildIds = buildRepository.findAll()
            .stream()
            .filter(b -> suiteName == null || suiteName.equals(b.getSuiteName()))
            .map(Build::getBuildId)
            .collect(Collectors.toList());
        
        return testResultRepository.findAll()
            .stream()
            .filter(t -> (suiteName == null || buildIds.contains(t.getBuildId()))
                    && t.getStatus() == TestStatus.FAIL)
            .collect(Collectors.groupingBy(
                TestResult::getFeature,
                Collectors.counting()
            ));
    }
    
    public Map<String, Object> getBuildDurationStats(String suiteName) {
        Map<String, Object> stats = new HashMap<>();
        
        // Get builds filtered by suite
        List<Build> allBuilds = buildRepository.findAll()
            .stream()
            .filter(b -> suiteName == null || suiteName.equals(b.getSuiteName()))
            .collect(Collectors.toList());
        
        List<Map<String, Object>> buildDurations = new ArrayList<>();
        
        for (Build build : allBuilds) {
            String durationStr = build.getTotalDuration();
            if (durationStr != null && !durationStr.equals("N/A")) {
                // Parse duration string like "104h 32m 31s" or "5m 30s"
                long durationMs = parseDurationString(durationStr);
                
                if (durationMs > 0) {
                    Map<String, Object> buildInfo = new HashMap<>();
                    buildInfo.put("buildId", build.getBuildId());
                    buildInfo.put("duration", durationMs);
                    buildInfo.put("testCount", build.getTotalTests());
                    buildDurations.add(buildInfo);
                }
            }
        }
        
        // Sort by duration descending
        buildDurations.sort((a, b) -> Long.compare(
            (Long)b.get("duration"), 
            (Long)a.get("duration")
        ));
        
        // Calculate statistics
        if (!buildDurations.isEmpty()) {
            long totalDuration = buildDurations.stream()
                .mapToLong(b -> (Long)b.get("duration"))
                .sum();
            long averageDuration = totalDuration / buildDurations.size();
            long longestDuration = (Long)buildDurations.get(0).get("duration");
            String longestBuildId = (String)buildDurations.get(0).get("buildId");
            
            stats.put("averageDuration", averageDuration);
            stats.put("longestDuration", longestDuration);
            stats.put("longestBuildId", longestBuildId);
            stats.put("totalBuilds", buildDurations.size());
            stats.put("recentBuilds", buildDurations.stream().limit(10).collect(Collectors.toList()));
        }
        
        return stats;
    }
    
    private long parseDurationString(String durationStr) {
        try {
            // Pattern to match "104h 32m 31s+135ms" or "5m 30s" or "1m"
            Pattern pattern = Pattern.compile("(?:([\\d]+)h\\s*)?(?:([\\d]+)m\\s*)?(?:([\\d]+)s\\s*)?(?:\\+([\\d]+)ms)?");
            Matcher matcher = pattern.matcher(durationStr.trim());
            
            if (matcher.matches()) {
                long hours = matcher.group(1) != null ? Long.parseLong(matcher.group(1)) : 0;
                long minutes = matcher.group(2) != null ? Long.parseLong(matcher.group(2)) : 0;
                long seconds = matcher.group(3) != null ? Long.parseLong(matcher.group(3)) : 0;
                long ms = matcher.group(4) != null ? Long.parseLong(matcher.group(4)) : 0;
                
                return hours * 3600000L + minutes * 60000L + seconds * 1000L + ms;
            }
        } catch (Exception e) {
            System.err.println("Error parsing duration string: " + durationStr + " - " + e.getMessage());
        }
        return 0;
    }
}