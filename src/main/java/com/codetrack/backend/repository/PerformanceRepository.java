package com.codetrack.backend.repository;

import com.codetrack.backend.entity.Performance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PerformanceRepository extends JpaRepository<Performance, UUID> {
    Optional<Performance> findByStudentId(UUID studentId);
}
