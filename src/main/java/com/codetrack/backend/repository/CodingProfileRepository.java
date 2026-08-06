package com.codetrack.backend.repository;

import com.codetrack.backend.entity.CodingProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CodingProfileRepository extends JpaRepository<CodingProfile, UUID> {
    Optional<CodingProfile> findByStudentId(UUID studentId);

    boolean existsByLeetcodeUsernameIgnoreCase(String leetcodeUsername);

    boolean existsByLeetcodeUsernameIgnoreCaseAndStudentIdNot(String leetcodeUsername, UUID studentId);

    boolean existsByCodeforcesUsernameIgnoreCase(String codeforcesUsername);

    boolean existsByCodeforcesUsernameIgnoreCaseAndStudentIdNot(String codeforcesUsername, UUID studentId);

    boolean existsByCodechefUsernameIgnoreCase(String codechefUsername);

    boolean existsByCodechefUsernameIgnoreCaseAndStudentIdNot(String codechefUsername, UUID studentId);
}
