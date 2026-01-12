package com.qa.dashboard.service;

import com.qa.dashboard.entity.Build;
import com.qa.dashboard.entity.TestResult;
import com.qa.dashboard.entity.TestStatus;
import com.qa.dashboard.repository.BuildRepository;
import com.qa.dashboard.repository.TestResultRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.LocalTime;
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
    
    public List<Build> getRecentBuilds(String suiteName, int limit, LocalDateTime fromDate, LocalDateTime toDate) {
        if (fromDate != null || toDate != null) {
            LocalDateTime endDate = toDate != null ? toDate.with(LocalTime.MAX) : null;
            
            if (fromDate != null && endDate != null) {
                return buildRepository.findBySuiteNameAndTimestampBetweenOrderByTimestampDesc(
                    suiteName, fromDate, endDate, PageRequest.of(0, limit));
            } else if (fromDate != null) {
                // Only from date - get all builds from fromDate to now
                return buildRepository.findBySuiteNameAndTimestampBetweenOrderByTimestampDesc(
                    suiteName, fromDate, LocalDateTime.now(), PageRequest.of(0, limit));
            } else if (endDate != null) {
                // Only to date - get all builds from beginning to endDate
                return buildRepository.findBySuiteNameAndTimestampBetweenOrderByTimestampDesc(
                    suiteName, LocalDateTime.of(1970, 1, 1, 0, 0), endDate, PageRequest.of(0, limit));
            }
        }
        return buildRepository.findBySuiteNameOrderByTimestampDesc(suiteName, 
            PageRequest.of(0, limit));
    }
    
    public List<Build> getAllBuilds(String suiteName, LocalDateTime fromDate, LocalDateTime toDate) {
        if (fromDate != null || toDate != null) {
            LocalDateTime endDate = toDate != null ? toDate.with(LocalTime.MAX) : null;
            
            if (fromDate != null && endDate != null) {
                return buildRepository.findBySuiteNameAndTimestampBetweenOrderByTimestampDesc(
                    suiteName, fromDate, endDate);
            } else if (fromDate != null) {
                return buildRepository.findBySuiteNameAndTimestampBetweenOrderByTimestampDesc(
                    suiteName, fromDate, LocalDateTime.now());
            } else if (endDate != null) {
                return buildRepository.findBySuiteNameAndTimestampBetweenOrderByTimestampDesc(
                    suiteName, LocalDateTime.of(1970, 1, 1, 0, 0), endDate);
            }
        }
        return buildRepository.findAll()
            .stream()
            .filter(b -> suiteName == null || suiteName.equals(b.getSuiteName()))
            .sorted((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()))
            .collect(Collectors.toList());
    }
    
    public Build getLatestBuild(String suiteName, LocalDateTime fromDate, LocalDateTime toDate) {
        if (fromDate != null || toDate != null) {
            LocalDateTime endDate = toDate != null ? toDate.with(LocalTime.MAX) : null;
            
            if (fromDate != null && endDate != null) {
                return buildRepository.findFirstBySuiteNameAndTimestampBetweenOrderByTimestampDesc(
                    suiteName, fromDate, endDate);
            } else if (fromDate != null) {
                return buildRepository.findFirstBySuiteNameAndTimestampBetweenOrderByTimestampDesc(
                    suiteName, fromDate, LocalDateTime.now());
            } else if (endDate != null) {
                return buildRepository.findFirstBySuiteNameAndTimestampBetweenOrderByTimestampDesc(
                    suiteName, LocalDateTime.of(1970, 1, 1, 0, 0), endDate);
            }
        }
        return buildRepository.findFirstBySuiteNameOrderByTimestampDesc(suiteName);
    }
    
    private List<String> getBuildIdsInDateRange(String suiteName, LocalDateTime fromDate, LocalDateTime toDate) {
        List<Build> builds;
        if (fromDate != null || toDate != null) {
            LocalDateTime endDate = toDate != null ? toDate.with(LocalTime.MAX) : null;
            
            if (fromDate != null && endDate != null) {
                builds = buildRepository.findBySuiteNameAndTimestampBetweenOrderByTimestampDesc(
                    suiteName, fromDate, endDate);
            } else if (fromDate != null) {
                builds = buildRepository.findBySuiteNameAndTimestampBetweenOrderByTimestampDesc(
                    suiteName, fromDate, LocalDateTime.now());
            } else if (endDate != null) {
                builds = buildRepository.findBySuiteNameAndTimestampBetweenOrderByTimestampDesc(
                    suiteName, LocalDateTime.of(1970, 1, 1, 0, 0), endDate);
            } else {
                builds = buildRepository.findAll()
                    .stream()
                    .filter(b -> suiteName == null || suiteName.equals(b.getSuiteName()))
                    .collect(Collectors.toList());
            }
        } else {
            builds = buildRepository.findAll()
                .stream()
                .filter(b -> suiteName == null || suiteName.equals(b.getSuiteName()))
                .collect(Collectors.toList());
        }
        return builds.stream().map(Build::getBuildId).collect(Collectors.toList());
    }
    
    public List<Map<String, Object>> getFlakyTests(String suiteName, int limit, LocalDateTime fromDate, LocalDateTime toDate) {
        // Get builds for the suite within date range
        List<String> buildIds = getBuildIdsInDateRange(suiteName, fromDate, toDate);
        
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
            
            // Count unique builds, not total records (to handle duplicates)
            // Group by buildId to get unique builds per test
            Map<String, List<TestResult>> resultsByBuild = results.stream()
                .collect(Collectors.groupingBy(TestResult::getBuildId));
            
            long totalRuns = resultsByBuild.size(); // Count unique builds
            long failures = resultsByBuild.entrySet().stream()
                .filter(e -> e.getValue().stream().anyMatch(r -> r.getStatus() == TestStatus.FAIL))
                .count(); // Count unique builds that had at least one failure
            
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
    
    public List<Map<String, Object>> getSlowestTests(String suiteName, int limit, LocalDateTime fromDate, LocalDateTime toDate) {
        // Get builds for the suite within date range
        List<String> buildIds = getBuildIdsInDateRange(suiteName, fromDate, toDate);
        
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
    
    public Map<String, Long> getFailuresByFeature(String suiteName, LocalDateTime fromDate, LocalDateTime toDate) {
        // Get builds for the suite within date range
        List<String> buildIds = getBuildIdsInDateRange(suiteName, fromDate, toDate);
        
        // Prepare module buckets
        Map<String, Long> failuresByModule = new LinkedHashMap<>();
        failuresByModule.put("Filter layer / Filter", 0L);
        failuresByModule.put("Widget", 0L);
        failuresByModule.put("Top nudges", 0L);
        failuresByModule.put("Search form", 0L);
        failuresByModule.put("Tuple", 0L);
        
        // Explicit test-name lists per module, based on user classification
        Set<String> filterTests = new HashSet<>(Arrays.asList(
            "Validate Presence of SortBy Section on Com Buy SRP Page",
            "Validate Investment Options filter on buy SRP Page",
            "Validate Minimum Budget filter on buy SRP Page",
            "Validate Maximum Budget filter on buy SRP Page",
            "Validate Sort By options value on SRP for Comm Buy",
            "Validate Sort By options value on SRP for Coworking",
            "Validate Presence of SortBy Section on Res Buy SRP Page",
            "Validate BHK filter on buy SRP Page",
            "Validate Property Type filter on buy SRP Page",
            "Validate Sort By options value on SRP for Res Buy",
            "Validate Presence of SortBy Section on Com Lease SRP Page",
            "Validate Sort By options value on SRP for Comm Lease",
            "ValidateConstructionStatusRemovedFiltersPlotLandFilter",
            "ValidateAmenitiesStatusRemovedFiltersPlotLandFilter",
            "ValidateFurnishingStatusRemovedFiltersPlotLandFilter",
            "ValidateInvestmentOptionsRemovedFiltersPlotLandFilter",
            "ValidatePurchaseTypeRemovedFiltersPlotLandFilter",
            "ValidateAdditionalAmenitiesPlotLandFilter",
            "ValidateCovidFiltersPlotLandFilter",
            "ValidateFoodandDrinksPlotLandFilter",
            "ValidateInternetandElectricityPlotLandFilter",
            "ValidateSpaceAccessPlotLandFilter",
            "ValidateServicesPlotLandFilter",
            "ValidateOtherFiltersPlotLandFilter",
            "ValidateActivitiesPlotLandFilter",
            "ValidateSRPOrderPlotLandFilter for Residential",
            "ValidateSRPOrderCOmmPlotLandFilter for commercial",
            "Validate filters Corner Plot In Pref Plot Land Res",
            "Validate filters In Gated Society In Pref Plot Land Res",
            "Validate facing direction residential",
            "Validate Presence of SortBy Section on Res PG SRP Page",
            "Validate Sort By options value on SRP for PG",
            "Validate Presence of SortBy Section on Res Rent SRP Page",
            "Validate Sort By options value on SRP for Res Rent",
            "Validate Sort By options value on SRP for Comm Project",
            "Validate Sort By options value on SRP for Res Project",
            "Validate Sorting on NPSRP for Commercial",
            "Validate Sorting on NPSRP for Residential",
            "Validate BHK on Res_Buy SRP Page from Filter Screen",
            "Validate Property Type on Res_Buy SRP Page from Filter Screen",
            "Validate Possession Status on Res_Buy SRP Page from Filter Screen",
            "Validate Posted By on Res_Buy SRP Page from Filter Screen",
            "Validate Localities on Res_Buy SRP Page from Filter Screen",
            "Validate New Booking / Resale on Res_Buy SRP Page from Filter Screen",
            "Validate Amenities & Facilities on Res_Buy SRP Page from Filter Screen",
            "Validate Bathrooms on Res_Buy SRP Page from Filter Screen",
            "Validate Furnishing Status on Res_Buy SRP Page from Filter Screen",
            "Validate RERA Approved on Res_Buy SRP Page from Filter Screen",
            "Validate Photos & Videos on Res_Buy SRP Page from Filter Screen",
            "Validate Bedrooms on Res_Rent SRP Page from Filter Screen",
            "Validate Property Type on Res_Rent SRP Page from Filter Screen",
            "Validate Furnishing Status on Res_Rent SRP Page from Filter Screen",
            "Validate Posted By on Res_Rent SRP Page from Filter Screen",
            "Validate With Videos on Res_Rent SRP Page from Filter Screen",
            "Validate Available for on Res_PG SRP Page from Filter Screen",
            "Validate Furnishing Status on Res_PG SRP Page from Filter Screen",
            "Validate Property Type on Res_PG SRP Page from Filter Screen",
            "Validate PG Services on Res_PG SRP Page from Filter Screen",
            "Validate Amenities & Facilities on Res_PG SRP Page from Filter Screen",
            "Validate Localities on Res_PG SRP Page from Filter Screen",
            "Validate Posted By on Res_PG SRP Page from Filter Screen",
            "Validate PG Occupancy on Res_PG SRP Page from Filter Screen",
            "Validate Bedrooms on Res_PG SRP Page from Filter Screen",
            "Validate With Videos on Res_PG SRP Page from Filter Screen",
            "Validate Property Type on Comm_Buy SRP Page from Filter Screen",
            "Validate Investment Type on Comm_Buy SRP Page from Filter Screen",
            "Validate Localities on Comm_Buy SRP Page from Filter Screen",
            "Validate Possession Status on Comm_Buy SRP Page from Filter Screen",
            "Validate New Booking / Resale on Comm_Buy SRP Page from Filter Screen",
            "Validate Amenities & Facilities on Comm_Buy SRP Page from Filter Screen",
            "Validate Posted By on Comm_Buy SRP Page from Filter Screen",
            "Validate Videos on Comm_Buy SRP Page from Filter Screen",
            "Validate Localities on Comm_Rent SRP Page from Filter Screen",
            "Validate New Projects/Societies on Comm_Rent SRP Page from Filter Screen",
            "Validate Amenities & Facilities on Comm_Rent SRP Page from Filter Screen",
            "Validate Posted By on Comm_Rent SRP Page from Filter Screen",
            "Validate Floor preference on Comm_Rent SRP Page from Filter Screen",
            "Validate Facilities on Comm_Rent SRP Page from Filter Screen",
            "Validate Key office specifications on Comm_Rent SRP Page from Filter Screen",
            "Validate Visual Filter (VF) Layer on IA Com Buy SRP Page",
            "Validate Visual Filter (VF) Layer on IA Com Lease SRP Page",
            "Validate Visual Filter (VF) Layer on IA Res Buy SRP Page",
            "Validate Visual Filter (VF) Layer on IA Res PG SRP Page",
            "Validate Visual Filter (VF) Layer on IA Res Rent SRP Page",
            "Validate Inline Filters on Res Buy SRP Page",
            "Validate Presence of SortBy and Filter Section on Res Buy SRP Page",
            "Validate Presence of SortBy and Filter Section on Res Rent SRP Page",
            "Validate Presence of SortBy and Filter Section on Res PG SRP Page",
            "Validate Presence of SortBy and Filter Section on Com Buy SRP Page",
            "Validate Presence of SortBy and Filter Section on Com Lease SRP Page",
            "Validate Inline filters on Res PG SRP Page",
            "Residential buy search's filter",
            "Residential Rent search's filter",
            "Residential PG search's filter",
            "Comm buy search's filter"
        ));
        
        Set<String> widgetTests = new HashSet<>(Arrays.asList(
            "Validate SEO Quick Link on Com Buy SRP Page",
            "Validate Fat Footer on Com Buy SRP Page",
            "Validate Widgets on Com Buy SRP Page",
            "Validate Widgets on Com Buy Locality SRP Page",
            "Left Panel Widgets on Commercial Buy",
            "Validate Feedback Option on Com Buy SRP Page",
            "Validate Popular Localities Section on Com Buy SRP Page",
            "Validate New Launch SRP on Res Buy SRP Page",
            "Validate Fat Footer on Coworking SRP Page",
            "Left Panel Widgets on Residential Buy",
            "Validate Widgets on SRP for Coworking",
            "Validate Presence of FAQ section on Res Buy SRP Page",
            "Validate SEO Quick Link on Res Buy SRP Page",
            "Validate Feedback Option on Res Buy SRP Page",
            "Validate Popular Localities Section at bottom on Res Buy SRP Page",
            "Validate Fat Footer on Res Buy SRP Page",
            "Validate Widgets on Res Buy SRP Page on City",
            "Validate Widgets on Res Buy SRP Page on Locality",
            "Validate Similar SAB on Res Buy SRP Page",
            "Validate FRAUD ALERT banner on SRP for Res_Rent",
            "Validate FRAUD ALERT banner on SRP for Res_PG",
            "Validate FRAUD ALERT banner on SRP for Res_Buy",
            "Validate FRAUD ALERT banner on SRP for Com_Buy",
            "Validate FRAUD ALERT banner on SRP for Com_Lease",
            "Validate Hamburger on SRP for Res_Buy",
            "Validate Hamburger on SRP for Res_Rent",
            "Validate Hamburger on SRP for Res_PG",
            "Validate Hamburger on SRP for Com_Buy",
            "Validate Hamburger on SRP for Com_Lease",
            "Validate Similar Properties on SRP for Res_Buy",
            "Validate Fat Footer on Com Lease Bareshell Office SRP Page",
            "Validate Fat Footer on Com Lease R2M Office SRP Page",
            "Validate Fat Footer on Com Lease SRP Page",
            "Validate Presence of widgets on Com Lease SRP Page",
            "Validate Presence of widgets on Com Lease Locality SRP Page",
            "Left Panel Widgets on Comm Lease Bareshell",
            "Left Panel Widgets on Comm Lease R2M",
            "Validate SEO Quick Link on Com Lease SRP Page",
            "Validate Feedback Option on Com Lease SRP Page",
            "Validate Fat Footer on Land/Plot Comm SRP Page",
            "Validate Fat Footer on Land/Plot Res SRP Page",
            "ValidateApprovedByAuthorityOnSRPinNoida",
            "ValidateApprovedByAuthorityOnSRPinNoidaComm",
            "Validate Feedback Option on RES Buy SRP Page",
            "Validate Feedback Option on Com lease SRP Page",
            "Validate Fat Footer on PG SRP Page",
            "Left Panel Widgets on PG",
            "Validate SEO Quick Link on Res PG SRP Page",
            "Validate Feedback Option on Res PG SRP Page",
            "Validate Popular Localities Section on Res PG SRP Page",
            "Validate Fat Footer on Res Rent SRP Page",
            "Validate SEO Quick Link on Res Rent SRP Page",
            "Validate Feedback Option on Res Rent SRP Page",
            "Validate Presence of Widget section on Residential Rent SRP Page",
            "Validate Popular Localities Section on Res Rent SRP Page",
            "Validate H1 Section on Res Buy SRP Page",
            "Validate H1 Section on Res Rent SRP Page",
            "Validate H1 Section on Res PG SRP Page",
            "Validate H1 Section on Com Buy SRP Page",
            "Validate H1 Section on Com Lease SRP Page",
            "Validate Anchor Header Layer on IA Com Buy SRP Page",
            "Validate Anchor Header Layer on IA Com Lease SRP Page",
            "Validate Anchor Header Layer on IA Res Buy SRP Page Not Present",
            "Anchor Header Layer on IA Res PG SRP Page",
            "Validate Anchor Header Layer on IA Res Rent SRP Page",
            "Validate Tabs Layer on IA Com Buy SRP Page",
            "Validate Tabs Layer on IA Com Lease SRP Page",
            "Validate Tabs Layer on IA Res Buy SRP Page",
            "Tabs Layer on IA Res PG SRP Page",
            "Validate Tabs Layer on IA Res Rent SRP Page",
            "Validate Presence of Chatbot on Res Buy SRP Page",
            "Validate Presence of Chatbot on Res Rent SRP Page",
            "Validate Presence of Chatbot on Res PG SRP Page",
            "Validate Presence of Chatbot on Com Buy SRP Page",
            "Validate Presence of Chatbot on Com Lease SRP Page",
            "Validate Res NPSRP project heading",
            "Validate BOSS banner on SRP for",
            "Validate Comm NPSRP project heading",
            "Validate Widgets on SRP for Bareshell office for Lease",
            "Validate Widgets on SRP for Ready to Move Office for Lease",
            "Validate Get App button on SRP for Res_Buy",
            "Validate Get App button on SRP for Res_Rent",
            "Validate Get App button on SRP for Com_Buy",
            "Validate Widgets on Res Buy SRP Page",
            "Validate Presence of Widget section on Residential Rent SRP Page"
        ));
        
        Set<String> topNudgesTests = new HashSet<>(Arrays.asList(
            "Validate Top Nudges on Com Buy SRP Page",
            "Validate Top Nudges on Res Buy SRP Page",
            "Validate Top Nudges on Com Lease SRP Page",
            "Validate Top Nudges on Res Plot/Land SRP Page",
            "Validate Top Nudges on Com BUY SRP Plot land Page",
            "Validate Top Nudges on Res PG SRP Page",
            "Validate Top Nudges on Res Rent SRP Page",
            "Validate Nudge Icons count on Com Buy SRP Page"
        ));
        
        Set<String> searchFormTests = new HashSet<>(Arrays.asList(
            "Validate Landing page URL for Comm Buy",
            "Validate Search Count for Commercial Buy",
            "Validate Landing page URL for Coworking",
            "Validate Search Count for Coworking",
            "Validate Landing page URL for Res Buy",
            "Validate Search Count for Residential Buy",
            "Validate Voice Search Button on homepage",
            "Validate Voice Search Button on SRP on Res Buy",
            "Validate Voice Search Button on SRP on Res Rent",
            "Validate Voice Search Button on SRP on Comm Buy",
            "Validate Voice Search Button on SRP on Comm Lease",
            "Validate Voice Search Button on SRP on PG",
            "Validate Voice Search Button on SRP on CoWorking",
            "Validate Voice Search Button on SRP on Land and Plots SRP",
            "Validate Voice Search Button on SRP on Projects",
            "Validate Landing page URL for Comm Lease",
            "Validate Validate Search Count for Commercial Lease",
            "Validate City Search from Plot Land Search Option in homepage",
            "Validate Landing page URL for PG",
            "Validate Search Count for PG",
            "Validate Landing page URL for Res Rent",
            "Validate Search Count for Residential Rent",
            "Validate Search Count for Dealer",
            "Validate Landing page URL for Comm Projects",
            "Validate Landing page URL for Res Projects",
            "Validate Pagination for Comm Projects",
            "Validate Pagination for Res Projects",
            "Validate Search Count for Projects - Commercial",
            "Validate Search Count for Projects - Residential",
            "Validate Search Input Box Presence on homepage",
            "Validate Voice Search Button on Search Form",
            "Validate Voice Search Button and Overlay on Homepage",
            "Validate Voice Search Button and Overlay on Search Form",
            "Validate Search Input Box Default Text on homepage",
            "Validate Search Icon Section on Res Buy SRP Page",
            "Validate Search Icon Section on Res Rent SRP Page",
            "Validate Search Icon Section on Res PG SRP Page",
            "Validate Search Icon Section on Com Buy SRP Page",
            "Validate Search Icon Section on Com Lease SRP Page",
            "Validate placeholder text on Res search form",
            "Validate near me icon search form"
        ));
        
        Set<String> tupleTests = new HashSet<>(Arrays.asList(
            "Validate Verified Tag Popup on Res Buy",
            "Validate Verified Tag Popup on Res Rent",
            "Validate Verified Tag Popup on PG",
            "Validate Verified Tag Popup on Comm Buy",
            "Validate Verified Tag Popup on Comm Lease",
            "Validate one click opening of XID from suggestions",
            "Validate Res NPSRP Tuple count",
            "Validate Comm NPSRP Tuple count"
        ));
        
        // Count failures by module based on explicit test-name lists
        testResultRepository.findAll()
            .stream()
            .filter(t -> (suiteName == null || buildIds.contains(t.getBuildId()))
                    && t.getStatus() == TestStatus.FAIL)
            .forEach(t -> {
                String testName = t.getTestName();
                
                if (filterTests.contains(testName)) {
                    failuresByModule.computeIfPresent("Filter layer / Filter", (k, v) -> v + 1);
                } else if (widgetTests.contains(testName)) {
                    failuresByModule.computeIfPresent("Widget", (k, v) -> v + 1);
                } else if (topNudgesTests.contains(testName)) {
                    failuresByModule.computeIfPresent("Top nudges", (k, v) -> v + 1);
                } else if (searchFormTests.contains(testName)) {
                    failuresByModule.computeIfPresent("Search form", (k, v) -> v + 1);
                } else if (tupleTests.contains(testName)) {
                    failuresByModule.computeIfPresent("Tuple", (k, v) -> v + 1);
                }
                // Any tests not in the explicit lists are ignored, as per requirement
            });
        
        // Remove modules with zero failures so the chart only shows modules that actually failed
        return failuresByModule.entrySet()
            .stream()
            .filter(e -> e.getValue() > 0)
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue,
                (a, b) -> a,
                LinkedHashMap::new
            ));
    }
    
    public Map<String, Object> getBuildDurationStats(String suiteName, LocalDateTime fromDate, LocalDateTime toDate) {
        Map<String, Object> stats = new HashMap<>();
        
        // Get builds filtered by suite and date range
        List<Build> allBuilds;
        if (fromDate != null || toDate != null) {
            LocalDateTime endDate = toDate != null ? toDate.with(LocalTime.MAX) : null;
            
            if (fromDate != null && endDate != null) {
                allBuilds = buildRepository.findBySuiteNameAndTimestampBetweenOrderByTimestampDesc(
                    suiteName, fromDate, endDate);
            } else if (fromDate != null) {
                allBuilds = buildRepository.findBySuiteNameAndTimestampBetweenOrderByTimestampDesc(
                    suiteName, fromDate, LocalDateTime.now());
            } else if (endDate != null) {
                allBuilds = buildRepository.findBySuiteNameAndTimestampBetweenOrderByTimestampDesc(
                    suiteName, LocalDateTime.of(1970, 1, 1, 0, 0), endDate);
            } else {
                allBuilds = buildRepository.findAll()
                    .stream()
                    .filter(b -> suiteName == null || suiteName.equals(b.getSuiteName()))
                    .collect(Collectors.toList());
            }
        } else {
            allBuilds = buildRepository.findAll()
                .stream()
                .filter(b -> suiteName == null || suiteName.equals(b.getSuiteName()))
                .collect(Collectors.toList());
        }
        
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