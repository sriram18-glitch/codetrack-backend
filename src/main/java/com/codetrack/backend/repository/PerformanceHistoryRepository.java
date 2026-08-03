package com.codetrack.backend.repository;

import com.codetrack.backend.entity.PerformanceHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PerformanceHistoryRepository extends JpaRepository<PerformanceHistory, UUID> {
    List<PerformanceHistory> findByStudentIdOrderByCapturedAtAsc(UUID studentId);
    long countByStudentId(UUID studentId);
}
