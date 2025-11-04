package com.qa.dashboard.entity;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "builds")
public class Build {
    @Id
    private String buildId;

    @Column(name = "suite_name", nullable = false)
    private String suiteName;

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    @Column(name = "total_tests")
    private Integer totalTests;

    @Column(name = "passed")
    private Integer passed;

    @Column(name = "failed")
    private Integer failed;

    @Column(name = "pass_rate")
    private Double passRate;

    @Column(name = "total_duration")
    private String totalDuration;

    // Constructors

    public Build() {}

    public Build(String buildId, String suiteName, LocalDateTime timestamp) {
        this.buildId = buildId;
        this.suiteName = suiteName;
        this.timestamp = timestamp;
    }

    // Getters and Setters
    
    public String getBuildId() {
        return buildId;
    }

    public void setBuildId(String buildId) {
        this.buildId = buildId;
    }

    public String getSuiteName() {
        return suiteName;
    }

    public void setSuiteName(String suiteName) {
        this.suiteName = suiteName;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public Integer getTotalTests() {
        return totalTests;
    }

    public void setTotalTests(Integer totalTests) {
        this.totalTests = totalTests;
    }

    public Integer getPassed() {
        return passed;
    }

    public void setPassed(Integer passed) {
        this.passed = passed;
    }

    public Integer getFailed() {
        return failed;
    }

    public void setFailed(Integer failed) {
        this.failed = failed;
    }

    public Double getPassRate() {
        return passRate;
    }

    public void setPassRate(Double passRate) {
        this.passRate = passRate;
    }

    public String getTotalDuration() {
        return totalDuration;
    }

public void setTotalDuration(String totalDuration) {
        this.totalDuration = totalDuration;
    }
}