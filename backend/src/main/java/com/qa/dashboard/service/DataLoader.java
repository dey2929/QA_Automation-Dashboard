package com.qa.dashboard.service;

import com.qa.dashboard.entity.Build;
import com.qa.dashboard.entity.TestResult;
import com.qa.dashboard.repository.BuildRepository;
import com.qa.dashboard.repository.TestResultRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
public class DataLoader implements CommandLineRunner {
    
    @Autowired
    private BuildRepository buildRepository;
    
    @Autowired
    private TestResultRepository testResultRepository;
    
    @Autowired
    private JenkinsReportParser reportParser;
    
    @Override
    public void run(String... args) throws Exception {
        if (buildRepository.count() == 0) {
            // Load data from HTML reports
            String reportsDirectory = Paths.get("..", "jenkins-parser").toAbsolutePath().toString();
            File reportsDir = new File(reportsDirectory);
            
            if (reportsDir.exists()) {
                System.out.println("Loading reports from: " + reportsDirectory);
                
                // Scan all suite subdirectories
                File[] suiteDirs = reportsDir.listFiles(File::isDirectory);
                List<Build> allBuilds = new ArrayList<>();
                
                if (suiteDirs != null && suiteDirs.length > 0) {
                    // Process each suite directory
                    for (File suiteDir : suiteDirs) {
                        String suiteName = suiteDir.getName();
                        System.out.println("\nProcessing suite directory: " + suiteName);
                        
                        List<Build> builds = reportParser.parseAllReports(suiteDir.getAbsolutePath());
                        
                        if (!builds.isEmpty()) {
                            File[] files = suiteDir.listFiles((d, name) -> name.endsWith(".html"));
                            
                            for (Build build : builds) {
                                buildRepository.save(build);
                                System.out.println("Loaded Build #" + build.getBuildId() + 
                                                  " (" + suiteName + ") - " + build.getPassed() + "/" + build.getTotalTests() + 
                                                  " passed (" + String.format("%.2f", build.getPassRate()) + "%)");
                                
                                // Parse and save individual test results
                                if (files != null) {
                                    for (File file : files) {
                                        // Match file by buildId (handles both numeric build IDs and date-time formats)
                                        String fileName = file.getName();
                                        String buildId = build.getBuildId();
                                        
                                        // For date-time format buildIds (e.g., "2025-08-02_16-26"), match against filename pattern
                                        boolean matches = false;
                                        if (buildId.contains("-") && buildId.contains("_")) {
                                            // Date-time format: convert back to match filename pattern
                                            // buildId: "2025-08-02_16-26" -> filename pattern: "2025-08-02 16.26"
                                            // Replace underscore with space, and replace last dash with dot
                                            String dateTimePattern = buildId.replace("_", " ");
                                            // Replace the dash between hour and minute (last dash)
                                            int lastDashIndex = dateTimePattern.lastIndexOf("-");
                                            if (lastDashIndex > 0) {
                                                dateTimePattern = dateTimePattern.substring(0, lastDashIndex) + "." + dateTimePattern.substring(lastDashIndex + 1);
                                            }
                                            matches = fileName.contains(dateTimePattern) || fileName.contains(buildId);
                                        } else {
                                            // Numeric build ID format
                                            matches = fileName.contains(buildId);
                                        }
                                        
                                        if (matches) {
                                            List<TestResult> testResults = reportParser.parseTestResults(file, build.getBuildId());
                                            for (TestResult testResult : testResults) {
                                                testResultRepository.save(testResult);
                                            }
                                            System.out.println("  -> Loaded " + testResults.size() + " test cases for Build #" + build.getBuildId());
                                            break;
                                        }
                                    }
                                }
                            }
                            
                            allBuilds.addAll(builds);
                        }
                    }
                } else {
                    // Fallback: try to load from root directory (for backward compatibility)
                    System.out.println("No suite subdirectories found, checking root directory...");
                    List<Build> builds = reportParser.parseAllReports(reportsDirectory);
                    
                    if (!builds.isEmpty()) {
                        File[] files = reportsDir.listFiles((d, name) -> name.endsWith(".html"));
                        
                        for (Build build : builds) {
                            buildRepository.save(build);
                            System.out.println("Loaded Build #" + build.getBuildId() + 
                                              " - " + build.getPassed() + "/" + build.getTotalTests() + 
                                              " passed (" + String.format("%.2f", build.getPassRate()) + "%)");
                            
                            if (files != null) {
                                for (File file : files) {
                                    // Match file by buildId (handles both numeric build IDs and date-time formats)
                                    String fileName = file.getName();
                                    String buildId = build.getBuildId();
                                    
                                    // For date-time format buildIds (e.g., "2025-08-02_16-26"), match against filename pattern
                                    boolean matches = false;
                                    if (buildId.contains("-") && buildId.contains("_")) {
                                        // Date-time format: convert back to match filename pattern
                                        String dateTimePattern = buildId.replace("_", " ").replace("-", ".");
                                        matches = fileName.contains(dateTimePattern) || fileName.contains(buildId);
                                    } else {
                                        // Numeric build ID format
                                        matches = fileName.contains(buildId);
                                    }
                                    
                                    if (matches) {
                                        List<TestResult> testResults = reportParser.parseTestResults(file, build.getBuildId());
                                        for (TestResult testResult : testResults) {
                                            testResultRepository.save(testResult);
                                        }
                                        System.out.println("  -> Loaded " + testResults.size() + " test cases for Build #" + build.getBuildId());
                                        break;
                                    }
                                }
                            }
                        }
                        
                        allBuilds.addAll(builds);
                    }
                }
                
                if (!allBuilds.isEmpty()) {
                    System.out.println("\nSuccessfully loaded " + allBuilds.size() + " builds with test case details!");
                } else {
                    System.out.println("No builds found in reports. Loading sample data...");
                    loadSampleData();
                }
            } else {
                System.out.println("Reports directory not found at: " + reportsDirectory);
                System.out.println("Loading sample data instead...");
                loadSampleData();
            }
        } else {
            System.out.println("Database already contains data. Skipping load.");
        }
    }
    
    private void loadSampleData() {
        // Keep the original sample data as fallback
        Build build1 = new Build("559", "Search_TestSuite", LocalDateTime.of(2025, 8, 2, 1, 49, 38));
        build1.setTotalTests(118);
        build1.setPassed(116);
        build1.setFailed(2);
        build1.setPassRate(98.31);
        build1.setTotalDuration("0h 45m 12s");
        
        Build build2 = new Build("562", "Search_TestSuite", LocalDateTime.of(2025, 8, 5, 1, 49, 50));
        build2.setTotalTests(118);
        build2.setPassed(117);
        build2.setFailed(1);
        build2.setPassRate(99.15);
        build2.setTotalDuration("0h 46m 02s");
        
        buildRepository.save(build1);
        buildRepository.save(build2);
        
        System.out.println("Sample data loaded successfully!");
    }
}