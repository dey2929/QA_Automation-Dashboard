// API Configuration
const API_BASE_URL = 'http://localhost:8080/api/dashboard';
let SUITE_NAME = 'Search_TestSuite'; // Make it changeable

// Global data storage
let dashboardData = {
    builds: [],
    latestBuild: null
};

// Initialize dashboard
document.addEventListener('DOMContentLoaded', function() {
    loadDashboardData();
    
    // Add refresh button functionality
    document.getElementById('refreshBtn').addEventListener('click', loadDashboardData);
    
    // Add suite selection change handler
    document.getElementById('suiteSelect').addEventListener('change', function() {
        SUITE_NAME = this.value;
        loadDashboardData();
    });
});

async function loadDashboardData() {
    console.log('Loading dashboard data for suite: ' + SUITE_NAME);
    
    try {
        // Fetch recent builds
        const response = await fetch(`${API_BASE_URL}/builds/${SUITE_NAME}`);
        if (!response.ok) {
            throw new Error('Failed to fetch builds');
        }
        
        const builds = await response.json();
        dashboardData.builds = builds.reverse(); // Show oldest to newest
        dashboardData.latestBuild = builds.length > 0 ? builds[builds.length - 1] : null;
        
        console.log(`Loaded ${builds.length} builds`);
        
        // Update UI
        updateMetrics();
        createPassRateChart();
        createFlakyTestsList();
        createSlowTestsList();
        createFeatureChart();
        createBuildDurationStats();
        
    } catch (error) {
        console.error('Error loading dashboard data:', error);
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
    document.getElementById('totalTests').textContent = latestBuild.totalTests;
    
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

function calculateAverageDuration() {
    if (!dashboardData.builds || dashboardData.builds.length === 0) {
        return 'N/A';
    }
    
    // Calculate average tests across all builds as a proxy for duration
    const avgTests = Math.round(
        dashboardData.builds.reduce((sum, b) => sum + b.totalTests, 0) / dashboardData.builds.length
    );
    return `${avgTests} tests avg`;
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
            labels: dashboardData.builds.map(b => `Build ${b.buildId}`),
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
        const response = await fetch(`${API_BASE_URL}/flaky-tests?suiteName=${SUITE_NAME}`);
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

async function createSlowTestsList() {
    const container = document.getElementById('slowTestsList');
    
    try {
        const response = await fetch(`${API_BASE_URL}/slowest-tests?suiteName=${SUITE_NAME}`);
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
        const response = await fetch(`${API_BASE_URL}/failures-by-feature?suiteName=${SUITE_NAME}`);
        const failuresByFeature = await response.json();
        
        if (Object.keys(failuresByFeature).length === 0) {
            // Fallback to build-level data
            const totalFailed = dashboardData.builds.reduce((sum, b) => sum + b.failed, 0);
            const totalPassed = dashboardData.builds.reduce((sum, b) => sum + b.passed, 0);
            
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
            // Use feature-level data
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
        // Fallback to build-level data
        const totalFailed = dashboardData.builds.reduce((sum, b) => sum + b.failed, 0);
        const totalPassed = dashboardData.builds.reduce((sum, b) => sum + b.passed, 0);
        
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

async function createBuildDurationStats() {
    const container = document.getElementById('buildDurationStats');
    
    try {
        const response = await fetch(`${API_BASE_URL}/build-duration-stats?suiteName=${SUITE_NAME}`);
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
                <div class="duration-stat">
                    <div class="duration-label">Average Duration</div>
                    <div class="duration-value">${averageDuration}</div>
                </div>
                <div class="duration-stat">
                    <div class="duration-label">Longest Build</div>
                    <div class="duration-value">${longestDuration}</div>
                    <div class="duration-sublabel">Build #${longestBuildId}</div>
                </div>
                <div class="duration-stat">
                    <div class="duration-label">Total Builds</div>
                    <div class="duration-value">${totalBuilds}</div>
                </div>
            </div>
            <div class="recent-builds">
                <h4>Recent Build Durations</h4>
                <div class="build-list">
                    ${recentBuilds.slice(0, 5).map(build => `
                        <div class="build-item">
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
