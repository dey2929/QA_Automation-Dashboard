package com.qa.dashboard.repository;

import com.qa.dashboard.entity.TestResult;
import com.qa.dashboard.entity.TestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TestResultRepository extends JpaRepository<TestResult, Long> {
    List<TestResult> findByBuildId(String buildId);
    List<TestResult> findByTestNameAndStatus(String testName, TestStatus status);
    long countByBuildId(String buildId);
    long countByTestNameAndStatus(String testName, TestStatus status);
    List<TestResult> findByFeature(String feature);
}

