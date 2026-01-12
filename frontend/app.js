// API Configuration
const API_BASE_URL = 'http://localhost:8080/api/dashboard';
let SUITE_NAME = 'Search_TestSuite'; // Make it changeable

// Global data storage
let dashboardData = {
    builds: [], // Limited to 10 for chart display
    allBuilds: [], // All builds for metrics calculation
    latestBuild: null
};

// Shimmer loading state
let shimmerStartTime = null;
const MIN_SHIMMER_TIME = 1000; // 1 second minimum

// Date formatting functions
function formatDateToDDMMYYYY(date) {
    if (!date) return '';
    const d = new Date(date);
    const day = String(d.getDate()).padStart(2, '0');
    const month = String(d.getMonth() + 1).padStart(2, '0');
    const year = d.getFullYear();
    return `${day}/${month}/${year}`;
}

function parseDDMMYYYYToISO(dateStr) {
    if (!dateStr || dateStr.trim() === '') return '';
    
    // Remove any slashes or spaces
    const cleaned = dateStr.replace(/[\/\s-]/g, '');
    
    // Check if it's 8 digits (DDMMYYYY)
    if (cleaned.length !== 8 || !/^\d+$/.test(cleaned)) {
        return '';
    }
    
    const day = cleaned.substring(0, 2);
    const month = cleaned.substring(2, 4);
    const year = cleaned.substring(4, 8);
    
    // Validate date
    const date = new Date(year, month - 1, day);
    if (date.getDate() != day || date.getMonth() != month - 1 || date.getFullYear() != year) {
        return '';
    }
    
    // Return in ISO format (YYYY-MM-DD) for API
    return `${year}-${month}-${day}`;
}

function formatDateInput(input) {
    let value = input.value.replace(/\D/g, ''); // Remove non-digits
    
    if (value.length >= 2) {
        value = value.substring(0, 2) + '/' + value.substring(2);
    }
    if (value.length >= 5) {
        value = value.substring(0, 5) + '/' + value.substring(5, 9);
    }
    
    input.value = value;
}

// Initialize dashboard
document.addEventListener('DOMContentLoaded', function() {
    // Initialize date range defaults (30 days ago to today)
    const today = new Date();
    const thirtyDaysAgo = new Date();
    thirtyDaysAgo.setDate(today.getDate() - 30);
    
    // Initialize Flatpickr date pickers with DD/MM/YYYY format
    const fromDatePicker = flatpickr('#fromDate', {
        dateFormat: 'd/m/Y',
        defaultDate: thirtyDaysAgo,
        maxDate: today,
        allowInput: true,
        clickOpens: true,
        onChange: function(selectedDates, dateStr, instance) {
            loadDashboardData();
        }
    });
    
    const toDatePicker = flatpickr('#toDate', {
        dateFormat: 'd/m/Y',
        defaultDate: today,
        maxDate: today,
        allowInput: true,
        clickOpens: true,
        onChange: function(selectedDates, dateStr, instance) {
            // Ensure toDate is not before fromDate
            if (fromDatePicker.selectedDates.length > 0 && selectedDates.length > 0) {
                if (selectedDates[0] < fromDatePicker.selectedDates[0]) {
                    alert('To Date cannot be before From Date');
                    instance.setDate(today);
                    return;
                }
            }
            loadDashboardData();
        }
    });
    
    // Update fromDate picker to ensure toDate is not before fromDate
    fromDatePicker.config.onChange.push(function(selectedDates, dateStr, instance) {
        if (selectedDates.length > 0 && toDatePicker.selectedDates.length > 0) {
            if (toDatePicker.selectedDates[0] < selectedDates[0]) {
                toDatePicker.setDate(selectedDates[0]);
            }
            toDatePicker.set('minDate', selectedDates[0]);
        }
    });
    
    loadDashboardData();
    
    // Add sync button functionality
    document.getElementById('syncBtn').addEventListener('click', syncWithJenkins);
    
    // Add refresh button functionality
    document.getElementById('refreshBtn').addEventListener('click', loadDashboardData);
    
    // Add suite selection change handler
    document.getElementById('suiteSelect').addEventListener('change', function() {
        SUITE_NAME = this.value;
        loadDashboardData();
    });
});

async function syncWithJenkins() {
    const syncBtn = document.getElementById('syncBtn');
    const originalText = syncBtn.innerHTML;
    
    // Show loading state
    syncBtn.disabled = true;
    syncBtn.innerHTML = '<span class="sync-icon">⏳</span> Syncing...';
    
    // Show shimmer and progress bar
    showShimmer();
    showProgressBar();
    setProgressBarText('Syncing with Jenkins...');
    updateProgressBar(10);
    
    try {
        updateProgressBar(30);
        setProgressBarText('Fetching reports from Jenkins...');
        
        const response = await fetch('http://localhost:8080/api/admin/sync-reports', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            }
        });
        
        updateProgressBar(60);
        setProgressBarText('Processing reports...');
        
        const result = await response.json();
        
        if (response.ok && result.status === 'success') {
            updateProgressBar(80);
            setProgressBarText('Loading data into database...');
            
            // Show success message with new builds count
            const newBuilds = result.newBuilds || 0;
            let message = 'Successfully synced reports from Jenkins!';
            if (newBuilds > 0) {
                message += ` ${newBuilds} new build(s) loaded.`;
            } else {
                message += ' No new builds found.';
            }
            showNotification(message, 'success');
            
            updateProgressBar(100);
            setProgressBarText('Sync completed!');
            
            // Hide shimmer after minimum display time, then reload dashboard data
            hideShimmer();
            setTimeout(() => {
                loadDashboardData();
            }, 1000);
        } else {
            throw new Error(result.message || 'Failed to sync reports');
        }
    } catch (error) {
        console.error('Error syncing with Jenkins:', error);
        setProgressBarText('Error syncing with Jenkins');
        hideShimmer();
        hideProgressBar();
        showNotification('Error syncing with Jenkins: ' + error.message, 'error');
    } finally {
        // Restore button state
        syncBtn.disabled = false;
        syncBtn.innerHTML = originalText;
    }
}

async function loadDashboardData() {
    console.log('Loading dashboard data for suite: ' + SUITE_NAME);
    
    // Show shimmer and progress bar
    showShimmer();
    showProgressBar();
    setProgressBarText('Loading dashboard data...');
    
    try {
        updateProgressBar(20);
        
        // Fetch recent builds with date range
        const dateRange = getDateRangeFromPickers();
        let buildsUrl = `${API_BASE_URL}/builds/${SUITE_NAME}`;
        const buildParams = new URLSearchParams();
        if (dateRange.fromDate) buildParams.append('fromDate', dateRange.fromDate);
        if (dateRange.toDate) buildParams.append('toDate', dateRange.toDate);
        if (buildParams.toString()) buildsUrl += '?' + buildParams.toString();
        
        const response = await fetch(buildsUrl);
        if (!response.ok) {
            throw new Error('Failed to fetch builds');
        }
        
        updateProgressBar(40);
        
        const builds = await response.json();
        dashboardData.builds = builds.reverse(); // Show oldest to newest (limited to 10 for chart)
        dashboardData.latestBuild = builds.length > 0 ? builds[builds.length - 1] : null;
        
        console.log(`Loaded ${builds.length} builds for chart`);
        
        updateProgressBar(50);
        setProgressBarText('Loading all builds for metrics...');
        
        // Fetch ALL builds for metrics calculation (no limit)
        const allBuildsUrl = `${API_BASE_URL}/builds/${SUITE_NAME}/all`;
        const allBuildsParams = new URLSearchParams();
        if (dateRange.fromDate) allBuildsParams.append('fromDate', dateRange.fromDate);
        if (dateRange.toDate) allBuildsParams.append('toDate', dateRange.toDate);
        const allBuildsResponse = await fetch(allBuildsUrl + (allBuildsParams.toString() ? '?' + allBuildsParams.toString() : ''));
        
        if (allBuildsResponse.ok) {
            const allBuilds = await allBuildsResponse.json();
            dashboardData.allBuilds = allBuilds.reverse(); // Show oldest to newest
            console.log(`Loaded ${allBuilds.length} total builds for metrics`);
        } else {
            // Fallback to limited builds if all builds fetch fails
            dashboardData.allBuilds = dashboardData.builds;
            console.warn('Failed to fetch all builds, using limited builds for metrics');
        }
        
        updateProgressBar(60);
        setProgressBarText('Updating charts and metrics...');
        
        // Update UI
        updateMetrics();
        updateProgressBar(65);
        createPassRateChart();
        updateProgressBar(70);
        await createFlakyTestsList();
        updateProgressBar(75);
        await createSlowTestsList();
        updateProgressBar(80);
        await createFeatureChart();
        updateProgressBar(85);
        await createBuildDurationStats();
        
        updateProgressBar(100);
        setProgressBarText('Data loaded successfully!');
        
        // Hide shimmer (with minimum 1 second display) and progress bar
        hideShimmer();
        setTimeout(() => {
            hideProgressBar();
        }, 500);
        
    } catch (error) {
        console.error('Error loading dashboard data:', error);
        setProgressBarText('Error loading data');
        hideShimmer();
        hideProgressBar();
        // Fallback to sample data if API fails
        loadFallbackData();
    }
}

function loadFallbackData() {
    console.log('Loading fallback sample data');
    dashboardData.builds = [
        {
            buildId: "559",
            timestamp: "2025-08-02T01:49:38Z",
            suiteName: "Search_TestSuite",
            totalTests: 118,
            passed: 116,
            failed: 2,
            passRate: 98.31
        },
        {
            buildId: "562",
            timestamp: "2025-08-05T01:49:50Z",
            suiteName: "Search_TestSuite",
            totalTests: 118,
            passed: 117,
            failed: 1,
            passRate: 99.15
        }
    ];
    dashboardData.latestBuild = dashboardData.builds[dashboardData.builds.length - 1];
    
    updateMetrics();
    createPassRateChart();
}

function updateMetrics() {
    const latestBuild = dashboardData.latestBuild;
    
    if (!latestBuild) {
        console.error('No build data available');
        return;
    }
    
    document.getElementById('latestBuildId').textContent = latestBuild.buildId;
    document.getElementById('overallPassRate').textContent = `${latestBuild.passRate.toFixed(2)}%`;
    
    // Calculate cumulative total tests across all builds in the selected date range
    const buildsForCalculation = dashboardData.allBuilds && dashboardData.allBuilds.length > 0 
        ? dashboardData.allBuilds 
        : dashboardData.builds;
    
    const totalTestsInRange = buildsForCalculation.reduce((sum, build) => {
        return sum + (build.totalTests || 0);
    }, 0);
    
    document.getElementById('totalTests').textContent = totalTestsInRange;
    
    // Update build status to show pass/total instead of PASS/FAIL
    const statusElement = document.getElementById('latestBuildStatus');
    // Ensure we have numeric values, handle both passed/passedCount and totalTests/testCount
    const passedCount = latestBuild.passed || latestBuild.passedCount || 0;
    const totalCount = latestBuild.totalTests || latestBuild.testCount || 0;
    
    // Always show numbers, never show "PASS" or "FAIL" text
    statusElement.textContent = `${passedCount}/${totalCount}`;
    
    // Update card styling based on pass/fail status (but don't change text)
    const hasFailures = (latestBuild.failed || latestBuild.failedCount || 0) > 0;
    statusElement.parentElement.className = !hasFailures 
        ? 'metric-card metric-card--success' 
        : 'metric-card metric-card--fail';
    
    // Update average duration
    const avgDuration = calculateAverageDuration();
    document.getElementById('avgDuration').textContent = avgDuration;
}

function parseDurationString(durationStr) {
    if (!durationStr || durationStr === 'N/A') {
        return 0;
    }
    
    // Pattern to match "104h 32m 31s+135ms" or "5m 30s" or "1m"
    const pattern = /(?:(\d+)h\s*)?(?:(\d+)m\s*)?(?:(\d+)s\s*)?(?:\+(\d+)ms)?/;
    const match = durationStr.trim().match(pattern);
    
    if (match) {
        const hours = match[1] ? parseInt(match[1], 10) : 0;
        const minutes = match[2] ? parseInt(match[2], 10) : 0;
        const seconds = match[3] ? parseInt(match[3], 10) : 0;
        const ms = match[4] ? parseInt(match[4], 10) : 0;
        
        return hours * 3600000 + minutes * 60000 + seconds * 1000 + ms;
    }
    
    return 0;
}

function calculateAverageDuration() {
    // Use allBuilds for metrics calculation, not just the limited builds for chart
    const buildsForCalculation = dashboardData.allBuilds && dashboardData.allBuilds.length > 0 
        ? dashboardData.allBuilds 
        : dashboardData.builds; // Fallback to limited builds if allBuilds not available
    
    if (!buildsForCalculation || buildsForCalculation.length === 0) {
        return 'N/A';
    }
    
    // Parse durations and calculate average from ALL builds in date range
    const durations = buildsForCalculation
        .map(b => parseDurationString(b.totalDuration))
        .filter(d => d > 0); // Only include builds with valid durations
    
    if (durations.length === 0) {
        return 'N/A';
    }
    
    const avgDurationMs = durations.reduce((sum, d) => sum + d, 0) / durations.length;
    return formatBuildDuration(avgDurationMs);
}

let passRateChart = null;

function createPassRateChart() {
    const ctx = document.getElementById('passRateChart').getContext('2d');
    
    // Destroy existing chart if it exists
    if (passRateChart) {
        passRateChart.destroy();
    }
    
    passRateChart = new Chart(ctx, {
        type: 'line',
        data: {
            labels: dashboardData.builds.map(b => [`Build ${b.buildId}`, formatBuildDate(b.timestamp)]),
            datasets: [{
                label: 'Pass Rate (%)',
                data: dashboardData.builds.map(b => b.passRate),
                borderColor: '#28a745',
                backgroundColor: 'rgba(40, 167, 69, 0.1)',
                tension: 0.4
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            scales: {
                y: {
                    beginAtZero: true,
                    max: 100
                }
            }
        }
    });
}

async function createFlakyTestsList() {
    const container = document.getElementById('flakyTestsList');
    
    try {
        const dateRange = getDateRangeFromPickers();
        const params = new URLSearchParams();
        params.append('suiteName', SUITE_NAME);
        if (dateRange.fromDate) params.append('fromDate', dateRange.fromDate);
        if (dateRange.toDate) params.append('toDate', dateRange.toDate);
        
        const response = await fetch(`${API_BASE_URL}/flaky-tests?${params.toString()}`);
        const flakyTests = await response.json();
        
        if (flakyTests.length === 0) {
            container.innerHTML = '<div class="test-item">No flaky tests found</div>';
            return;
        }
        
        container.innerHTML = flakyTests.slice(0, 10).map(test => {
            const rate = test.failureRate;
            const level = rate > 50 ? 'high' : rate > 25 ? 'medium' : 'low';
            return `
                <div class="test-item failure-rate-${level}">
                    <div class="test-name" title="${test.testName}">${test.testName.substring(0, 60)}${test.testName.length > 60 ? '...' : ''}</div>
                    <div class="test-metric">${rate}% (${test.failures}/${test.totalRuns})</div>
                </div>
            `;
        }).join('');
    } catch (error) {
        console.error('Error loading flaky tests:', error);
        container.innerHTML = '<div class="test-item">Error loading flaky tests</div>';
    }
}

function formatTime(milliseconds) {
    const totalSeconds = Math.floor(milliseconds / 1000);
    const minutes = Math.floor(totalSeconds / 60);
    const seconds = totalSeconds % 60;
    
    if (minutes > 0) {
        return `${minutes} min ${seconds} secs`;
    } else {
        return `${seconds} secs`;
    }
}

function formatBuildDate(timestamp) {
    const date = new Date(timestamp);
    return date.toLocaleDateString('en-US', { 
        year: 'numeric', 
        month: 'short', 
        day: 'numeric' 
    }); // Returns "Nov 25, 2025"
}

// Helper function to get Flatpickr instance dates in ISO format
function getDateRangeFromPickers() {
    // Try to get dates from Flatpickr instances if available
    const fromDateInput = document.getElementById('fromDate');
    const toDateInput = document.getElementById('toDate');
    
    // Check if Flatpickr is initialized
    if (fromDateInput && fromDateInput._flatpickr && toDateInput && toDateInput._flatpickr) {
        const fromDate = fromDateInput._flatpickr.selectedDates[0];
        const toDate = toDateInput._flatpickr.selectedDates[0];
        
        if (fromDate) {
            const fromISO = fromDate.toISOString().split('T')[0];
            if (toDate) {
                const toISO = toDate.toISOString().split('T')[0];
                return { fromDate: fromISO, toDate: toISO };
            }
            return { fromDate: fromISO, toDate: null };
        }
    }
    
    // Fallback to text input parsing
    return getDateRange();
}

function getDateRange() {
    const fromDateInput = document.getElementById('fromDate').value;
    const toDateInput = document.getElementById('toDate').value;
    
    // Convert DD/MM/YYYY to YYYY-MM-DD for API
    const fromDate = parseDDMMYYYYToISO(fromDateInput);
    const toDate = parseDDMMYYYYToISO(toDateInput);
    
    return { fromDate, toDate };
}

async function createSlowTestsList() {
    const container = document.getElementById('slowTestsList');
    
    try {
        const dateRange = getDateRangeFromPickers();
        const params = new URLSearchParams();
        params.append('suiteName', SUITE_NAME);
        if (dateRange.fromDate) params.append('fromDate', dateRange.fromDate);
        if (dateRange.toDate) params.append('toDate', dateRange.toDate);
        
        const response = await fetch(`${API_BASE_URL}/slowest-tests?${params.toString()}`);
        const slowTests = await response.json();
        
        if (slowTests.length === 0) {
            container.innerHTML = '<div class="test-item">No test execution data available</div>';
            return;
        }
        
        container.innerHTML = slowTests.map(test => {
            const medianFormatted = formatTime(test.medianTime);
            const minFormatted = formatTime(test.minTime);
            const maxFormatted = formatTime(test.maxTime);
            const variance = ((test.maxTime - test.minTime) / test.medianTime * 100).toFixed(0);
            
            return `
                <div class="test-item">
                    <div class="test-name" title="${test.testName}">
                        ${test.testName.substring(0, 55)}${test.testName.length > 55 ? '...' : ''}
                    </div>
                    <div class="test-metric">
                        <div style="font-weight: bold; color: #d9534f;">${medianFormatted} (median)</div>
                        <div style="font-size: 0.85em; color: #888;">${minFormatted} - ${maxFormatted} (${variance}% variance)</div>
                        <div style="font-size: 0.85em; color: #888;">${test.runs} runs</div>
                    </div>
                </div>
            `;
        }).join('');
    } catch (error) {
        console.error('Error loading slow tests:', error);
        container.innerHTML = '<div class="test-item">Error loading slow tests</div>';
    }
}

let featureChart = null;

async function createFeatureChart() {
    const ctx = document.getElementById('featureChart').getContext('2d');
    
    // Destroy existing chart if it exists
    if (featureChart) {
        featureChart.destroy();
    }
    
    try {
        const dateRange = getDateRangeFromPickers();
        const params = new URLSearchParams();
        params.append('suiteName', SUITE_NAME);
        if (dateRange.fromDate) params.append('fromDate', dateRange.fromDate);
        if (dateRange.toDate) params.append('toDate', dateRange.toDate);
        
        const response = await fetch(`${API_BASE_URL}/failures-by-feature?${params.toString()}`);
        const failuresByFeature = await response.json();
        
        if (Object.keys(failuresByFeature).length === 0) {
            // Fallback to build-level data - use all builds for accurate metrics
            const buildsForFallback = dashboardData.allBuilds && dashboardData.allBuilds.length > 0 
                ? dashboardData.allBuilds 
                : dashboardData.builds;
            const totalFailed = buildsForFallback.reduce((sum, b) => sum + (b.failed || 0), 0);
            const totalPassed = buildsForFallback.reduce((sum, b) => sum + (b.passed || 0), 0);
            
            featureChart = new Chart(ctx, {
                type: 'pie',
                data: {
                    labels: ['Passed', 'Failed'],
                    datasets: [{
                        data: [totalPassed, totalFailed],
                        backgroundColor: ['#28a745', '#dc3545']
                    }]
                },
                options: {
                    responsive: true,
                    maintainAspectRatio: false
                }
            });
        } else {
            // Use module-level data
            const features = Object.keys(failuresByFeature);
            const failureCounts = features.map(f => failuresByFeature[f]);
            
            featureChart = new Chart(ctx, {
                type: 'pie',
                data: {
                    labels: features,
                    datasets: [{
                        data: failureCounts,
                        backgroundColor: ['#dc3545', '#ffc107', '#007bff', '#28a745', '#6f42c1']
                    }]
                },
                options: {
                    responsive: true,
                    maintainAspectRatio: false,
                    legend: {
                        display: true,
                        position: 'right'
                    }
                }
            });
        }
    } catch (error) {
        console.error('Error loading failures by feature:', error);
        // Fallback to build-level data - use all builds for accurate metrics
        const buildsForFallback = dashboardData.allBuilds && dashboardData.allBuilds.length > 0 
            ? dashboardData.allBuilds 
            : dashboardData.builds;
        const totalFailed = buildsForFallback.reduce((sum, b) => sum + (b.failed || 0), 0);
        const totalPassed = buildsForFallback.reduce((sum, b) => sum + (b.passed || 0), 0);
        
        featureChart = new Chart(ctx, {
            type: 'pie',
            data: {
                labels: ['Passed', 'Failed'],
                datasets: [{
                    data: [totalPassed, totalFailed],
                    backgroundColor: ['#28a745', '#dc3545']
                }]
            },
            options: {
                responsive: true
            }
        });
    }
}

function formatBuildDuration(milliseconds) {
    const totalSeconds = Math.floor(milliseconds / 1000);
    const hours = Math.floor(totalSeconds / 3600);
    const minutes = Math.floor((totalSeconds % 3600) / 60);
    const seconds = totalSeconds % 60;
    
    if (hours > 0) {
        return `${hours}h ${minutes}m ${seconds}s`;
    } else if (minutes > 0) {
        return `${minutes}m ${seconds}s`;
    } else {
        return `${seconds}s`;
    }
}

// Shimmer loading functions
function showShimmer() {
    shimmerStartTime = Date.now();
    const shimmerContainers = document.querySelectorAll('.shimmer-container');
    shimmerContainers.forEach(container => {
        container.classList.add('loading');
    });
}

function hideShimmer() {
    const shimmerContainers = document.querySelectorAll('.shimmer-container');
    
    if (!shimmerStartTime) {
        // If shimmer wasn't started, just remove the loading class immediately
        shimmerContainers.forEach(container => {
            container.classList.remove('loading');
        });
        return;
    }
    
    const elapsed = Date.now() - shimmerStartTime;
    const remainingTime = Math.max(0, MIN_SHIMMER_TIME - elapsed);
    
    setTimeout(() => {
        shimmerContainers.forEach(container => {
            container.classList.remove('loading');
        });
        shimmerStartTime = null;
    }, remainingTime);
}

// Progress bar functions
function showProgressBar() {
    const progressBar = document.getElementById('progressBar');
    if (progressBar) {
        progressBar.style.display = 'block';
        updateProgressBar(0);
    }
}

function hideProgressBar() {
    const progressBar = document.getElementById('progressBar');
    if (progressBar) {
        setTimeout(() => {
            progressBar.style.display = 'none';
            updateProgressBar(0);
        }, 300);
    }
}

function updateProgressBar(percentage) {
    const progressFill = document.querySelector('.progress-bar-fill');
    if (progressFill) {
        progressFill.style.width = `${Math.min(100, Math.max(0, percentage))}%`;
    }
}

function setProgressBarText(text) {
    const progressText = document.querySelector('.progress-bar-text');
    if (progressText) {
        progressText.textContent = text;
    }
}

// Notification system
function showNotification(message, type = 'info') {
    // Remove existing notification if any
    const existingNotification = document.querySelector('.notification');
    if (existingNotification) {
        existingNotification.remove();
    }
    
    // Create notification element
    const notification = document.createElement('div');
    notification.className = `notification notification--${type}`;
    notification.textContent = message;
    
    // Add to body
    document.body.appendChild(notification);
    
    // Show notification
    setTimeout(() => {
        notification.classList.add('notification--show');
    }, 10);
    
    // Auto-remove after 5 seconds
    setTimeout(() => {
        notification.classList.remove('notification--show');
        setTimeout(() => {
            notification.remove();
        }, 300);
    }, 5000);
}

async function createBuildDurationStats() {
    const container = document.getElementById('buildDurationStats');
    
    try {
        const dateRange = getDateRangeFromPickers();
        const params = new URLSearchParams();
        params.append('suiteName', SUITE_NAME);
        if (dateRange.fromDate) params.append('fromDate', dateRange.fromDate);
        if (dateRange.toDate) params.append('toDate', dateRange.toDate);
        
        const response = await fetch(`${API_BASE_URL}/build-duration-stats?${params.toString()}`);
        const stats = await response.json();
        
        if (!stats || Object.keys(stats).length === 0) {
            container.innerHTML = '<div class="duration-item">No build duration data available</div>';
            return;
        }
        
        const averageDuration = formatBuildDuration(stats.averageDuration || 0);
        const longestDuration = formatBuildDuration(stats.longestDuration || 0);
        const longestBuildId = stats.longestBuildId || 'N/A';
        const totalBuilds = stats.totalBuilds || 0;
        const recentBuilds = stats.recentBuilds || [];
        
        container.innerHTML = `
            <div class="duration-summary">
                <div class="duration-stat" title="Average time taken for all builds to complete execution in the selected time period">
                    <div class="duration-label">Average Duration</div>
                    <div class="duration-value">${averageDuration}</div>
                    <div class="duration-help">ℹ️ Avg time per build</div>
                </div>
                <div class="duration-stat" title="The build that took the longest time to complete execution">
                    <div class="duration-label">Longest Build</div>
                    <div class="duration-value">${longestDuration}</div>
                    <div class="duration-sublabel">Build #${longestBuildId}</div>
                    <div class="duration-help">ℹ️ Slowest build</div>
                </div>
                <div class="duration-stat" title="Total number of builds executed in the selected date range">
                    <div class="duration-label">Total Builds</div>
                    <div class="duration-value">${totalBuilds}</div>
                    <div class="duration-help">ℹ️ Builds in range</div>
                </div>
            </div>
            <div class="recent-builds">
                <h4>Recent Build Durations <span class="help-icon" title="Top 5 longest builds sorted by execution time (descending)">ℹ️</span></h4>
                <div class="build-list">
                    ${recentBuilds.slice(0, 5).map(build => `
                        <div class="build-item" title="Build #${build.buildId}: Duration ${formatBuildDuration(build.duration)}, ${build.testCount} tests executed">
                            <span class="build-id">#${build.buildId}</span>
                            <span class="build-duration">${formatBuildDuration(build.duration)}</span>
                            <span class="build-tests">${build.testCount} tests</span>
                        </div>
                    `).join('')}
                </div>
            </div>
        `;
        
    } catch (error) {
        console.error('Error loading build duration stats:', error);
        container.innerHTML = '<div class="duration-item">Error loading build duration data</div>';
    }
}
