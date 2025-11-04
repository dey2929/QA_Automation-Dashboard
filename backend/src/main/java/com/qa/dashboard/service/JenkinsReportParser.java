package com.qa.dashboard.service;

import com.qa.dashboard.entity.Build;
import com.qa.dashboard.entity.TestResult;
import com.qa.dashboard.entity.TestStatus;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class JenkinsReportParser {
    
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("MMM d, yyyy hh:mm:ss a");
    
    public Build parseReport(File htmlFile) {
        try {
            Document doc = Jsoup.parse(htmlFile, "UTF-8");
            
            // Extract build ID and suite name from filename
            String buildId = extractBuildId(htmlFile.getName());
            String suiteName = extractSuiteName(htmlFile.getName());
            
            // Extract timestamp
            LocalDateTime timestamp = extractTimestamp(doc);
            
            // Extract test statistics
            TestStats stats = extractTestStats(doc);
            
            // Create Build object
            Build build = new Build(buildId, suiteName, timestamp);
            build.setTotalTests(stats.totalTests);
            build.setPassed(stats.passed);
            build.setFailed(stats.failed);
            build.setPassRate(stats.passRate);
            build.setTotalDuration(stats.duration);
            
            return build;
            
        } catch (Exception e) {
            System.err.println("Error parsing report: " + htmlFile.getName());
            e.printStackTrace();
            return null;
        }
    }
    
    private String extractBuildId(String filename) {
        // Pattern 1: Match "Search_TestSuite_562.html" format (build number)
        Pattern pattern1 = Pattern.compile("(\\w+(?:_\\w+)?)_(\\d+)\\.html");
        Matcher matcher1 = pattern1.matcher(filename);
        if (matcher1.find()) {
            return matcher1.group(2); // Return the build number
        }
        
        // Pattern 2: Match "99acres_Homepage_Report_2025-08-02 16.26.html" format (date-time)
        Pattern pattern2 = Pattern.compile("99acres_Homepage_Report_(\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}\\.\\d{2})\\.html");
        Matcher matcher2 = pattern2.matcher(filename);
        if (matcher2.find()) {
            return matcher2.group(1).replace(" ", "_").replace(".", "-"); // Convert to buildId format: "2025-08-02_16-26"
        }
        
        return "UNKNOWN";
    }
    
    private String extractSuiteName(String filename) {
        // Pattern 1: Match "Search_TestSuite_562.html" format
        Pattern pattern1 = Pattern.compile("(\\w+(?:_\\w+)?)_(\\d+)\\.html");
        Matcher matcher1 = pattern1.matcher(filename);
        if (matcher1.find()) {
            return matcher1.group(1); // Return the suite name (e.g., "Search_TestSuite")
        }
        
        // Pattern 2: Match "99acres_Homepage_Report_2025-08-02 16.26.html" format
        Pattern pattern2 = Pattern.compile("99acres_Homepage_Report_.*\\.html");
        Matcher matcher2 = pattern2.matcher(filename);
        if (matcher2.find()) {
            return "HomePage_99acres"; // Map to the configured suite name
        }
        
        return "UNKNOWN";
    }
    
    private LocalDateTime extractTimestamp(Document doc) {
        try {
            Elements timestampElements = doc.select("span.suite-start-time");
            if (!timestampElements.isEmpty()) {
                String timestampStr = timestampElements.first().text();
                // Parse format: "Aug 5, 2025 01:42:05 AM"
                return LocalDateTime.parse(timestampStr, FORMATTER);
            }
        } catch (Exception e) {
            System.err.println("Error parsing timestamp: " + e.getMessage());
        }
        return LocalDateTime.now();
    }
    
    private TestStats extractTestStats(Document doc) {
        TestStats stats = new TestStats();
        
        try {
            // Count actual individual test cases instead of parentCount
            Elements testNodes = doc.select("li.node.level-1.leaf");
            
            stats.totalTests = testNodes.size();
            stats.passed = 0;
            stats.failed = 0;
            
            for (Element testNode : testNodes) {
                String status = testNode.attr("status");
                if ("pass".equalsIgnoreCase(status)) {
                    stats.passed++;
                } else if ("fail".equalsIgnoreCase(status)) {
                    stats.failed++;
                }
            }
            
            stats.passRate = stats.totalTests > 0 ? (double) stats.passed / stats.totalTests * 100 : 0;
            
            // Extract duration from HTML
            extractDuration(doc, stats);
            
        } catch (Exception e) {
            System.err.println("Error extracting test stats: " + e.getMessage());
            stats.totalTests = 0;
            stats.passed = 0;
            stats.failed = 0;
            stats.passRate = 0.0;
        }
        
        return stats;
    }
    
    private void extractDuration(Document doc, TestStats stats) {
        try {
            // Look for "Time Taken" panel (e.g., "104h 32m 31s+135ms")
            Elements durationElements = doc.select("div.panel-lead");
            
            for (Element elem : durationElements) {
                String text = elem.text().trim();
                
                // Match patterns like "104h 32m 31s+135ms" or "5m 30s"
                Pattern pattern = Pattern.compile("(?:([\\d]+)h\\s*)?(?:([\\d]+)m\\s*)?(?:([\\d]+)s\\s*)?(?:\\+([\\d]+)ms)?");
                Matcher matcher = pattern.matcher(text);
                
                if (matcher.matches()) {
                    long hours = matcher.group(1) != null ? Long.parseLong(matcher.group(1)) : 0;
                    long minutes = matcher.group(2) != null ? Long.parseLong(matcher.group(2)) : 0;
                    long seconds = matcher.group(3) != null ? Long.parseLong(matcher.group(3)) : 0;
                    long ms = matcher.group(4) != null ? Long.parseLong(matcher.group(4)) : 0;
                    
                    long totalMs = hours * 3600000L + minutes * 60000L + seconds * 1000L + ms;
                    
                    if (totalMs > 0) {
                        stats.duration = text;
                        return;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error extracting duration: " + e.getMessage());
        }
    }
    
    private static class TestStats {
        int totalTests;
        int passed;
        int failed;
        double passRate;
        String duration = "N/A";
    }
    
    public List<TestResult> parseTestResults(File htmlFile, String buildId) {
        List<TestResult> testResults = new ArrayList<>();
        
        try {
            Document doc = Jsoup.parse(htmlFile, "UTF-8");
            
            // Find all leaf test nodes (individual test cases)
            Elements testNodes = doc.select("li.node.level-1.leaf");
            
            for (Element testNode : testNodes) {
                String status = testNode.attr("status"); // "pass" or "fail"
                String testName = testNode.select("div.node-name").text();
                if (testName.startsWith("Test Case:")) {
                    testName = testName.substring("Test Case:".length()).trim();
                }
                // Limit test name to 500 characters
                if (testName.length() > 500) {
                    testName = testName.substring(0, 500);
                }
                
                // Extract feature from parent suite
                String feature = "Unknown";
                Element parentSuite = testNode.closest("li.test");
                if (parentSuite != null) {
                    Elements suiteName = parentSuite.select("span.test-name");
                    if (!suiteName.isEmpty()) {
                        feature = suiteName.first().text();
                    }
                }
                // Limit feature to 200 characters
                if (feature.length() > 200) {
                    feature = feature.substring(0, 200);
                }
                
                // Extract execution time from log timestamps
                int executionTime = calculateExecutionTimeFromLogs(testNode);
                
                // Extract error type if failed
                String errorType = null;
                if ("fail".equalsIgnoreCase(status)) {
                    Elements errorElements = testNode.select("textarea.code-block");
                    if (!errorElements.isEmpty()) {
                        String errorText = errorElements.first().text();
                        // Extract first line as error type, but limit to 200 characters
                        if (errorText.contains("\n")) {
                            String firstLine = errorText.substring(0, errorText.indexOf("\n"));
                            errorType = firstLine.substring(0, Math.min(200, firstLine.length()));
                        } else {
                            errorType = errorText.substring(0, Math.min(200, errorText.length()));
                        }
                    }
                }
                
                // Create TestResult
                TestResult testResult = new TestResult(testName, buildId, 
                    status.equals("pass") ? TestStatus.PASS : TestStatus.FAIL);
                testResult.setExecutionTime(executionTime);
                testResult.setErrorType(errorType);
                testResult.setFeature(feature);
                
                testResults.add(testResult);
            }
            
        } catch (Exception e) {
            System.err.println("Error parsing test results: " + htmlFile.getName());
            e.printStackTrace();
        }
        
        return testResults;
    }
    
    private int calculateExecutionTimeFromLogs(Element testNode) {
        // Extract all timestamps from the test logs
        Elements timestampElements = testNode.select("td.timestamp");
        
        if (timestampElements.size() < 2) {
            return 0; // Need at least 2 timestamps to calculate duration
        }
        
        // Get first and last timestamps
        String firstTime = timestampElements.first().text().trim();
        String lastTime = timestampElements.last().text().trim();
        
        try {
            // Parse timestamps (format: "1:42:33 AM" or "1:42:35 PM")
            LocalTime startTime = parseTimeString(firstTime);
            LocalTime endTime = parseTimeString(lastTime);
            
            if (startTime != null && endTime != null) {
                // Handle day rollover (e.g., 11:59 PM to 12:01 AM)
                Duration duration;
                if (endTime.isBefore(startTime) || endTime.equals(startTime)) {
                    // Assume same duration as logs are typically minutes apart
                    duration = Duration.ofHours(24).minus(Duration.between(endTime, startTime));
                } else {
                    duration = Duration.between(startTime, endTime);
                }
                
                return (int) duration.getSeconds() * 1000; // Convert to milliseconds
            }
        } catch (Exception e) {
            System.err.println("Error parsing timestamps: " + e.getMessage());
        }
        
        return 0;
    }
    
    private LocalTime parseTimeString(String timeStr) {
        try {
            // Parse format like "1:42:33 AM" or "11:45:22 PM"
            String[] parts = timeStr.split(" ");
            if (parts.length < 2) {
                return null;
            }
            
            String timePart = parts[0]; // "1:42:33"
            String ampm = parts[1];    // "AM" or "PM"
            
            String[] timeComponents = timePart.split(":");
            int hour = Integer.parseInt(timeComponents[0]);
            int minute = timeComponents.length > 1 ? Integer.parseInt(timeComponents[1]) : 0;
            int second = timeComponents.length > 2 ? Integer.parseInt(timeComponents[2]) : 0;
            
            // Convert to 24-hour format
            if (ampm.equalsIgnoreCase("PM") && hour != 12) {
                hour += 12;
            } else if (ampm.equalsIgnoreCase("AM") && hour == 12) {
                hour = 0;
            }
            
            return LocalTime.of(hour, minute, second);
        } catch (Exception e) {
            return null;
        }
    }
    
    public List<Build> parseAllReports(String reportsDirectory) {
        List<Build> builds = new ArrayList<>();
        File dir = new File(reportsDirectory);
        
        if (!dir.exists() || !dir.isDirectory()) {
            System.err.println("Directory does not exist: " + reportsDirectory);
            return builds;
        }
        
        File[] files = dir.listFiles((d, name) -> name.endsWith(".html"));
        if (files == null) {
            return builds;
        }
        
        for (File file : files) {
            System.out.println("Parsing: " + file.getName());
            Build build = parseReport(file);
            if (build != null) {
                builds.add(build);
            }
        }
        
        return builds;
    }
}

