package com.codetrack.backend.service;

import com.codetrack.backend.exception.ApiException;
import com.codetrack.backend.repository.CodingProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Guards the invariant that each coding platform username belongs to at most
 * one student. Called from registration and every admin edit path. When
 * {@code ownerStudentId} is supplied the student's own existing username is
 * allowed to stay unchanged; only a username owned by a different student is
 * rejected.
 */
@Component
@RequiredArgsConstructor
public class UsernameUniquenessValidator {

    private final CodingProfileRepository codingProfileRepository;

    public void validate(String leetcode, String codeforces, String codechef, UUID ownerStudentId) {
        if (ownerStudentId != null) {
            if (leetcode != null
                    && codingProfileRepository.existsByLeetcodeUsernameIgnoreCaseAndStudentIdNot(leetcode, ownerStudentId)) {
                throw new ApiException(HttpStatus.CONFLICT, "This LeetCode username is already registered.");
            }
            if (codeforces != null
                    && codingProfileRepository.existsByCodeforcesUsernameIgnoreCaseAndStudentIdNot(codeforces, ownerStudentId)) {
                throw new ApiException(HttpStatus.CONFLICT, "This Codeforces username is already registered.");
            }
            if (codechef != null
                    && codingProfileRepository.existsByCodechefUsernameIgnoreCaseAndStudentIdNot(codechef, ownerStudentId)) {
                throw new ApiException(HttpStatus.CONFLICT, "This CodeChef username is already registered.");
            }
        } else {
            if (leetcode != null && codingProfileRepository.existsByLeetcodeUsernameIgnoreCase(leetcode)) {
                throw new ApiException(HttpStatus.CONFLICT, "This LeetCode username is already registered.");
            }
            if (codeforces != null && codingProfileRepository.existsByCodeforcesUsernameIgnoreCase(codeforces)) {
                throw new ApiException(HttpStatus.CONFLICT, "This Codeforces username is already registered.");
            }
            if (codechef != null && codingProfileRepository.existsByCodechefUsernameIgnoreCase(codechef)) {
                throw new ApiException(HttpStatus.CONFLICT, "This CodeChef username is already registered.");
            }
        }
    }
}
