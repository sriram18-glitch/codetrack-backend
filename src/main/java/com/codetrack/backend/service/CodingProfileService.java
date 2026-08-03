package com.codetrack.backend.service;

import com.codetrack.backend.dto.CodingProfileRequest;
import com.codetrack.backend.dto.CodingProfileResponse;
import com.codetrack.backend.entity.CodingProfile;
import com.codetrack.backend.entity.Student;
import com.codetrack.backend.exception.ApiException;
import com.codetrack.backend.repository.CodingProfileRepository;
import com.codetrack.backend.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CodingProfileService {

    private final CodingProfileRepository codingProfileRepository;
    private final StudentRepository studentRepository;

    @Transactional
    public CodingProfileResponse upsertProfile(UUID studentId, CodingProfileRequest request) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Student not found"));

        CodingProfile profile = codingProfileRepository.findByStudentId(studentId)
                .orElseGet(() -> CodingProfile.builder().student(student).build());

        profile.setLeetcodeUsername(blankToNull(request.leetcodeUsername()));
        profile.setCodeforcesUsername(blankToNull(request.codeforcesUsername()));
        profile.setCodechefUsername(blankToNull(request.codechefUsername()));

        profile = codingProfileRepository.save(profile);
        log.info("Coding profile saved for student id={}", studentId);

        return toResponse(profile);
    }

    public CodingProfileResponse getProfile(UUID studentId) {
        CodingProfile profile = codingProfileRepository.findByStudentId(studentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "No coding profile found for this student"));
        return toResponse(profile);
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private CodingProfileResponse toResponse(CodingProfile profile) {
        return new CodingProfileResponse(
                profile.getId(),
                profile.getStudent().getId(),
                profile.getLeetcodeUsername(),
                profile.getCodeforcesUsername(),
                profile.getCodechefUsername()
        );
    }
}