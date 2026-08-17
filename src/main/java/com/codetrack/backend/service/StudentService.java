package com.codetrack.backend.service;

import com.codetrack.backend.dto.CreateStudentRequest;
import com.codetrack.backend.dto.StudentResponse;
import com.codetrack.backend.dto.UpdateStudentRequest;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class StudentService {

    private final StudentRepository studentRepository;
    private final CodingProfileRepository codingProfileRepository;
    private final PerformanceRepository performanceRepository;
    private final UsernameUniquenessValidator usernameUniquenessValidator;
    private final PlatformTransactionManager transactionManager;
    private final AutoSyncService autoSyncService;

    public StudentResponse createStudent(CreateStudentRequest request) {
        if (studentRepository.existsByRollNumberIgnoreCase(request.rollNumber())) {
            throw new ApiException(HttpStatus.CONFLICT, "A student with this roll number already exists");
        }
        if (studentRepository.existsByEmailIgnoreCase(request.email())) {
            throw new ApiException(HttpStatus.CONFLICT, "A student with this email already exists");
        }

        String leetcode = blankToNull(request.leetcodeUsername());
        String codeforces = blankToNull(request.codeforcesUsername());
        String codechef = blankToNull(request.codechefUsername());
        usernameUniquenessValidator.validate(leetcode, codeforces, codechef, null);

        Student student = new TransactionTemplate(transactionManager).execute(status -> {
            Student created = studentRepository.save(Student.builder()
                    .rollNumber(request.rollNumber())
                    .name(request.name())
                    .email(request.email())
                    .branch(normalize(request.branch()))
                    .year(request.year())
                    .section(normalize(request.section()))
                    .phone(blankToNull(request.phone()))
                    .githubProfileUrl(blankToNull(request.githubProfileUrl()))
                    .linkedinProfileUrl(blankToNull(request.linkedinProfileUrl()))
                    .build());

            codingProfileRepository.save(CodingProfile.builder()
                    .student(created)
                    .leetcodeUsername(leetcode)
                    .codeforcesUsername(codeforces)
                    .codechefUsername(codechef)
                    .build());

            performanceRepository.save(Performance.builder().student(created).build());
            return created;
        });

        log.info("Student created: id={}, rollNumber={}", student.getId(), student.getRollNumber());

        // Best-effort automatic sync. Runs after the create transaction commits
        // and never fails the creation.
        try {
            autoSyncService.syncStudentBestEffort(student.getId());
        } catch (Exception ex) {
            log.warn("Automatic sync after student creation failed for {}: {}", student.getRollNumber(), ex.getMessage());
        }

        return toResponse(student);
    }

    @Transactional(readOnly = true)
    public List<StudentResponse> listStudents() {
        return studentRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public StudentResponse getStudent(UUID id) {
        return toResponse(findById(id));
    }

    @Transactional(readOnly = true)
    public StudentResponse getByRollNumber(String rollNumber) {
        return studentRepository.findByRollNumberIgnoreCase(rollNumber)
                .map(this::toResponse)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "No student found with roll number " + rollNumber));
    }

    @Transactional(readOnly = true)
    public List<StudentResponse> searchStudents(String query) {
        String q = query == null ? "" : query.trim();
        if (q.isEmpty()) {
            return listStudents();
        }
        return studentRepository
                .findByRollNumberContainingIgnoreCaseOrNameContainingIgnoreCaseOrEmailContainingIgnoreCaseOrBranchContainingIgnoreCase(
                        q, q, q, q)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public StudentResponse updateStudent(UUID id, UpdateStudentRequest request) {
        Student student = findById(id);

        if (!student.getRollNumber().equalsIgnoreCase(request.rollNumber())
                && studentRepository.existsByRollNumberIgnoreCase(request.rollNumber())) {
            throw new ApiException(HttpStatus.CONFLICT, "A student with this roll number already exists");
        }
        if (!student.getEmail().equalsIgnoreCase(request.email())
                && studentRepository.existsByEmailIgnoreCase(request.email())) {
            throw new ApiException(HttpStatus.CONFLICT, "A student with this email already exists");
        }

        String leetcode = blankToNull(request.leetcodeUsername());
        String codeforces = blankToNull(request.codeforcesUsername());
        String codechef = blankToNull(request.codechefUsername());
        usernameUniquenessValidator.validate(leetcode, codeforces, codechef, student.getId());

        student.setRollNumber(request.rollNumber());
        student.setName(request.name());
        student.setEmail(request.email());
        student.setBranch(normalize(request.branch()));
        student.setYear(request.year());
        student.setSection(normalize(request.section()));
        student.setPhone(blankToNull(request.phone()));
        student.setGithubProfileUrl(blankToNull(request.githubProfileUrl()));
        student.setLinkedinProfileUrl(blankToNull(request.linkedinProfileUrl()));

        student = studentRepository.save(student);
        Student saved = student;

        CodingProfile profile = codingProfileRepository.findByStudentId(id)
                .orElseGet(() -> CodingProfile.builder().student(saved).build());
        profile.setLeetcodeUsername(leetcode);
        profile.setCodeforcesUsername(codeforces);
        profile.setCodechefUsername(codechef);
        codingProfileRepository.save(profile);

        log.info("Student updated: id={}, rollNumber={}", student.getId(), student.getRollNumber());

        return toResponse(student);
    }

    @Transactional
    public void deleteStudent(UUID id) {
        Student student = findById(id);
        studentRepository.delete(student);
        log.info("Student deleted: id={}, rollNumber={}", student.getId(), student.getRollNumber());
    }

    private Student findById(UUID id) {
        return studentRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Student not found"));
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private String normalize(String value) {
        String trimmed = blankToNull(value);
        return trimmed == null ? null : trimmed.toUpperCase();
    }

    private StudentResponse toResponse(Student student) {
        return new StudentResponse(
                student.getId(),
                student.getRollNumber(),
                student.getName(),
                student.getEmail(),
                student.getBranch(),
                student.getYear(),
                student.getSection(),
                student.getPhone(),
                student.getGithubProfileUrl(),
                student.getLinkedinProfileUrl(),
                student.getCreatedAt()
        );
    }
}
