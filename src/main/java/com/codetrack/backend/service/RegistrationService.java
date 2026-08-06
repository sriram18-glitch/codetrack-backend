package com.codetrack.backend.service;

import com.codetrack.backend.dto.RegisterRequest;
import com.codetrack.backend.dto.RegisterResponse;
import com.codetrack.backend.entity.CodingProfile;
import com.codetrack.backend.entity.Performance;
import com.codetrack.backend.entity.Student;
import com.codetrack.backend.exception.ApiException;
import com.codetrack.backend.repository.CodingProfileRepository;
import com.codetrack.backend.repository.PerformanceRepository;
import com.codetrack.backend.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Public self-registration. Validates platform usernames live using the same
 * platform services as the sync flow, then creates the student together with
 * an empty coding profile and an empty performance row in one transaction so
 * the student appears immediately on the admin Students page.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegistrationService {

    private final StudentRepository studentRepository;
    private final CodingProfileRepository codingProfileRepository;
    private final PerformanceRepository performanceRepository;
    private final LeetCodeService leetCodeService;
    private final CodeforcesService codeforcesService;
    private final CodeChefService codeChefService;
    private final UsernameUniquenessValidator usernameUniquenessValidator;

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        String rollNumber = request.rollNumber().trim();
        String email = request.email().trim();

        if (studentRepository.existsByRollNumberIgnoreCase(rollNumber)) {
            throw new ApiException(HttpStatus.CONFLICT, "A student with this roll number already exists");
        }
        if (studentRepository.existsByEmailIgnoreCase(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "A student with this email already exists");
        }

        String leetcode = trimToNull(request.leetcodeUsername());
        String codeforces = trimToNull(request.codeforcesUsername());
        String codechef = trimToNull(request.codechefUsername());

        if (leetcode != null && leetCodeService.fetch(leetcode).isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "LeetCode username '" + leetcode + "' does not exist");
        }
        if (codeforces != null && codeforcesService.fetch(codeforces).isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Codeforces username '" + codeforces + "' does not exist");
        }
        if (codechef != null && codeChefService.fetch(codechef).isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "CodeChef username '" + codechef + "' does not exist");
        }

        usernameUniquenessValidator.validate(leetcode, codeforces, codechef, null);

        String branch = normalize(request.branch());
        String section = normalize(request.section());

        Student student = Student.builder()
                .rollNumber(rollNumber)
                .name(request.name().trim())
                .email(email)
                .branch(branch)
                .year(request.year())
                .section(section)
                .phone(trimToNull(request.phone()))
                .build();
        student = studentRepository.save(student);

        codingProfileRepository.save(CodingProfile.builder()
                .student(student)
                .leetcodeUsername(leetcode)
                .codeforcesUsername(codeforces)
                .codechefUsername(codechef)
                .build());

        performanceRepository.save(Performance.builder().student(student).build());

        log.info("Student self-registered: id={}, rollNumber={}", student.getId(), student.getRollNumber());
        return new RegisterResponse(student.getId(), student.getRollNumber(), student.getName(),
                "Registration successful");
    }

    @Transactional(readOnly = true)
    public boolean validateUsername(String platform, String username) {
        if (username == null || username.isBlank()) {
            return false;
        }
        String handle = username.trim();
        return switch (platform.toLowerCase()) {
            case "leetcode", "lc" -> leetCodeService.fetch(handle).isPresent();
            case "codeforces", "cf" -> codeforcesService.fetch(handle).isPresent();
            case "codechef", "cc" -> codeChefService.fetch(handle).isPresent();
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "Unsupported platform: " + platform);
        };
    }

    private String trimToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private String normalize(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? null : trimmed.toUpperCase();
    }
}
