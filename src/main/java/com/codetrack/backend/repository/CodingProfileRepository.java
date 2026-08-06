package com.codetrack.backend.repository;

import com.codetrack.backend.entity.CodingProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CodingProfileRepository extends JpaRepository<CodingProfile, UUID> {
    Optional<CodingProfile> findByStudentId(UUID studentId);

    @Query("select cp from CodingProfile cp join fetch cp.student")
    List<CodingProfile> findAllWithStudent();

    boolean existsByLeetcodeUsernameIgnoreCase(String leetcodeUsername);

    boolean existsByLeetcodeUsernameIgnoreCaseAndStudentIdNot(String leetcodeUsername, UUID studentId);

    boolean existsByCodeforcesUsernameIgnoreCase(String codeforcesUsername);

    boolean existsByCodeforcesUsernameIgnoreCaseAndStudentIdNot(String codeforcesUsername, UUID studentId);

    boolean existsByCodechefUsernameIgnoreCase(String codechefUsername);

    boolean existsByCodechefUsernameIgnoreCaseAndStudentIdNot(String codechefUsername, UUID studentId);
}
