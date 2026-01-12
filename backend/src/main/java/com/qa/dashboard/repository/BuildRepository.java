package com.qa.dashboard.repository;

import com.qa.dashboard.entity.Build;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface BuildRepository extends JpaRepository<Build, String> {
    List<Build> findBySuiteNameOrderByTimestampDesc(String suiteName, Pageable pageable);
    Build findFirstBySuiteNameOrderByTimestampDesc(String suiteName);
    boolean existsByBuildId(String buildId);
    List<Build> findBySuiteNameAndTimestampBetweenOrderByTimestampDesc(
            String suiteName, LocalDateTime startDate, LocalDateTime endDate, Pageable pageable);
    List<Build> findBySuiteNameAndTimestampBetweenOrderByTimestampDesc(
            String suiteName, LocalDateTime startDate, LocalDateTime endDate);
    Build findFirstBySuiteNameAndTimestampBetweenOrderByTimestampDesc(
            String suiteName, LocalDateTime startDate, LocalDateTime endDate);
}   