package com.qa.dashboard.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.boot.CommandLineRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.text.SimpleDateFormat;
import java.util.Locale;

@Service
public class JenkinsReportFetcher implements CommandLineRunner {
    
    private static final String JENKINS_BASE_URL = "http://172.16.3.88:8080";
    private static final String LOCAL_REPORTS_DIR = "../jenkins-parser";
    
    // Define test suites configuration
    private static final Map<String, String> TEST_SUITES = new HashMap<String, String>() {{
        put("Search_TestSuite", "/job/Search_TestSuite/ws/resources/reports/");
        put("HomePage_99acres", "/job/HomePage_99acres/ws/resources/reports/");
    }};
    
    @Override
    public void run(String... args) throws Exception {
        System.out.println("=== Checking for new reports from Jenkins on startup ===");
        fetchReports();
    }
    
    /**
     * Fetch reports daily at 12:00 PM
     * Cron expression: second minute hour day month day-of-week
     * 0 0 12 * * ? = At 12:00 PM every day
     */
    @Scheduled(cron = "0 0 12 * * ?")
    public void fetchDailyReports() {
        System.out.println("=== Scheduled report fetch from Jenkins (12 PM daily) ===");
        fetchReports();
    }
    
    /**
     * Main method to fetch reports from all configured test suites
     */
    public void fetchReports() {
        int totalDownloaded = 0;
        int totalSkipped = 0;
        
        for (Map.Entry<String, String> suite : TEST_SUITES.entrySet()) {
            String suiteName = suite.getKey();
            String reportsDirUrl = suite.getValue();
            
            System.out.println("\n=== Fetching reports for " + suiteName + " ===");
            
            try {
                List<String> availableFiles = listAvailableReports(reportsDirUrl, suiteName);
                
                if (availableFiles.isEmpty()) {
                    System.out.println("No reports found for " + suiteName);
                    continue;
                }
                
                int downloaded = 0;
                int skipped = 0;
                
                for (String fileName : availableFiles) {
                    if (downloadReportIfNeeded(fileName, reportsDirUrl, suiteName)) {
                        downloaded++;
                    } else {
                        skipped++;
                    }
                }
                
                totalDownloaded += downloaded;
                totalSkipped += skipped;
                
                System.out.println("  " + suiteName + " sync completed:");
                System.out.println("    - Downloaded: " + downloaded + " new report(s)");
                System.out.println("    - Skipped: " + skipped + " existing report(s)");
                System.out.println("    - Total available: " + availableFiles.size() + " report(s)");
                
            } catch (Exception e) {
                System.err.println("Error fetching reports for " + suiteName + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        System.out.println("\n=== Overall Report Sync Summary ===");
        System.out.println("  - Total Downloaded: " + totalDownloaded + " report(s)");
        System.out.println("  - Total Skipped: " + totalSkipped + " report(s)");
    }
    
    private List<String> listAvailableReports(String reportsDirUrl, String suiteName) {
        List<String> files = new ArrayList<>();
        
        try {
            String reportsUrl = JENKINS_BASE_URL + reportsDirUrl;
            System.out.println("Fetching report list from: " + reportsUrl);
            
            Document doc = Jsoup.connect(reportsUrl)
                .timeout(10000)
                .get();
            
            // Fetch ALL HTML files from the reports directory (no filtering by name pattern)
            Elements links = doc.select("a[href$=.html]");
            
            for (Element link : links) {
                String href = link.attr("href");
                // Add all HTML files, regardless of naming pattern
                files.add(href);
            }
            
            System.out.println("Found " + files.size() + " HTML reports for " + suiteName);
            
        } catch (Exception e) {
            System.err.println("Error listing reports from Jenkins: " + e.getMessage());
        }
        
        return files;
    }
    
    private boolean downloadReportIfNeeded(String fileName, String reportsDirUrl, String suiteName) {
        try {
            // Create suite-specific directory: jenkins-parser/Search_TestSuite/ or jenkins-parser/HomePage_99acres/
            File suiteDir = new File(LOCAL_REPORTS_DIR, suiteName);
            if (!suiteDir.exists()) {
                suiteDir.mkdirs();
                System.out.println("Created directory for suite: " + suiteName);
            }
            
            File localFile = new File(suiteDir, fileName);
            
            if (localFile.exists()) {
                try {
                    long remoteTime = getRemoteFileTime(fileName, reportsDirUrl);
                    if (remoteTime > 0 && remoteTime <= localFile.lastModified()) {
                        System.out.println("Skipped (up to date): " + suiteName + "/" + fileName);
                        return false;
                    }
                } catch (Exception e) {
                    // If we can't check remote time, download anyway
                }
            }
            
            String fileUrl = JENKINS_BASE_URL + reportsDirUrl + fileName;
            System.out.println("Downloading: " + suiteName + "/" + fileName + " from Jenkins...");
            
            URL url = new URL(fileUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(10000);
            conn.connect();
            
            if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                InputStream in = conn.getInputStream();
                Files.copy(in, localFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                in.close();
                
                try {
                    long remoteTime = getRemoteFileTime(fileName, reportsDirUrl);
                    if (remoteTime > 0) {
                        localFile.setLastModified(remoteTime);
                    }
                } catch (Exception e) {
                    // Ignore
                }
                
                System.out.println("✓ Downloaded: " + suiteName + "/" + fileName);
                return true;
            } else {
                System.out.println("✗ Failed to download " + fileName + 
                                 " (HTTP " + conn.getResponseCode() + ")");
            }
            
        } catch (java.io.FileNotFoundException e) {
            System.out.println("Report not found on Jenkins: " + fileName);
        } catch (Exception e) {
            System.err.println("Error downloading " + fileName + ": " + e.getMessage());
        }
        
        return false;
    }
    
    private long getRemoteFileTime(String fileName, String reportsDirUrl) {
        try {
            String fileUrl = JENKINS_BASE_URL + reportsDirUrl + fileName;
            HttpURLConnection conn = (HttpURLConnection) new URL(fileUrl).openConnection();
            conn.setRequestMethod("HEAD");
            conn.setConnectTimeout(2000);
            conn.connect();
            
            String lastModified = conn.getHeaderField("Last-Modified");
            if (lastModified != null) {
                SimpleDateFormat sdf = new SimpleDateFormat(
                    "EEE, dd MMM yyyy HH:mm:ss zzz", Locale.ENGLISH);
                return sdf.parse(lastModified).getTime();
            }
        } catch (Exception e) {
            // Ignore errors in time checking
        }
        return 0;
    }
}

