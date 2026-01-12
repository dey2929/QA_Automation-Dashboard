package com.qa.dashboard.service;

import com.qa.dashboard.entity.Build;
import com.qa.dashboard.entity.TestResult;
import com.qa.dashboard.repository.BuildRepository;
import com.qa.dashboard.repository.TestResultRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@Service
public class DataReloadService {
    
    @Autowired
    private BuildRepository buildRepository;
    
    @Autowired
    private TestResultRepository testResultRepository;
    
    @Autowired
    private JenkinsReportParser reportParser;
    
    /**
     * Reload new reports that haven't been loaded yet
     * @return number of new builds loaded
     */
    public int reloadNewReports() {
        String reportsDirectory = Paths.get("..", "jenkins-parser").toAbsolutePath().toString();
        File reportsDir = new File(reportsDirectory);
        
        if (!reportsDir.exists()) {
            System.out.println("Reports directory not found: " + reportsDirectory);
            return 0;
        }
        
        int newBuildsCount = 0;
        
        // Scan all suite subdirectories
        File[] suiteDirs = reportsDir.listFiles(File::isDirectory);
        
        if (suiteDirs != null && suiteDirs.length > 0) {
            for (File suiteDir : suiteDirs) {
                String suiteName = suiteDir.getName();
                System.out.println("\nProcessing suite directory: " + suiteName);
                
                List<Build> builds = reportParser.parseAllReports(suiteDir.getAbsolutePath());
                
                if (!builds.isEmpty()) {
                    File[] files = suiteDir.listFiles((d, name) -> name.endsWith(".html"));
                    
                    for (Build build : builds) {
                        // Check if build already exists
                        if (buildRepository.findById(build.getBuildId()).isPresent()) {
                            System.out.println("Build #" + build.getBuildId() + " already exists, skipping...");
                            continue;
                        }
                        
                        // Save new build
                        buildRepository.save(build);
                        newBuildsCount++;
                        System.out.println("Loaded Build #" + build.getBuildId() + 
                                         " (" + suiteName + ") - " + build.getPassed() + "/" + build.getTotalTests() + 
                                         " passed (" + String.format("%.2f", build.getPassRate()) + "%)");
                        
                        // Parse and save individual test results
                        if (files != null) {
                            for (File file : files) {
                                String fileName = file.getName();
                                String buildId = build.getBuildId();
                                
                                boolean matches = false;
                                if (buildId.contains("-") && buildId.contains("_")) {
                                    String dateTimePattern = buildId.replace("_", " ");
                                    int lastDashIndex = dateTimePattern.lastIndexOf("-");
                                    if (lastDashIndex > 0) {
                                        dateTimePattern = dateTimePattern.substring(0, lastDashIndex) + "." + dateTimePattern.substring(lastDashIndex + 1);
                                    }
                                    matches = fileName.contains(dateTimePattern) || fileName.contains(buildId);
                                } else {
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
                }
            }
        } else {
            // Fallback: try to load from root directory
            System.out.println("No suite subdirectories found, checking root directory...");
            List<Build> builds = reportParser.parseAllReports(reportsDirectory);
            
            if (!builds.isEmpty()) {
                File[] files = reportsDir.listFiles((d, name) -> name.endsWith(".html"));
                
                for (Build build : builds) {
                    if (buildRepository.findById(build.getBuildId()).isPresent()) {
                        continue;
                    }
                    
                    buildRepository.save(build);
                    newBuildsCount++;
                    System.out.println("Loaded Build #" + build.getBuildId());
                    
                    if (files != null) {
                        for (File file : files) {
                            String fileName = file.getName();
                            String buildId = build.getBuildId();
                            
                            boolean matches = false;
                            if (buildId.contains("-") && buildId.contains("_")) {
                                String dateTimePattern = buildId.replace("_", " ").replace("-", ".");
                                matches = fileName.contains(dateTimePattern) || fileName.contains(buildId);
                            } else {
                                matches = fileName.contains(buildId);
                            }
                            
                            if (matches) {
                                List<TestResult> testResults = reportParser.parseTestResults(file, build.getBuildId());
                                for (TestResult testResult : testResults) {
                                    testResultRepository.save(testResult);
                                }
                                break;
                            }
                        }
                    }
                }
            }
        }
        
        System.out.println("\nTotal new builds loaded: " + newBuildsCount);
        return newBuildsCount;
    }
}

