package com.codetrack.backend.repository;

import com.codetrack.backend.entity.Student;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StudentRepository extends JpaRepository<Student, UUID> {
    Optional<Student> findByRollNumberIgnoreCase(String rollNumber);
    boolean existsByRollNumberIgnoreCase(String rollNumber);
    boolean existsByEmailIgnoreCase(String email);

    List<Student> findByRollNumberContainingIgnoreCaseOrNameContainingIgnoreCaseOrEmailContainingIgnoreCaseOrBranchContainingIgnoreCase(
            String rollNumber, String name, String email, String branch);
}
