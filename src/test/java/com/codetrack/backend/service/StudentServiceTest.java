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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentServiceTest {

    @Mock private StudentRepository studentRepository;
    @Mock private CodingProfileRepository codingProfileRepository;
    @Mock private PerformanceRepository performanceRepository;
    @Mock private UsernameUniquenessValidator usernameUniquenessValidator;
    @Mock private PlatformTransactionManager platformTransactionManager;
    @Mock private AutoSyncService autoSyncService;

    @Captor private ArgumentCaptor<Student> studentCaptor;
    @Captor private ArgumentCaptor<CodingProfile> profileCaptor;
    @Captor private ArgumentCaptor<Performance> performanceCaptor;

    private StudentService studentService;

    @BeforeEach
    void setUp() {
        studentService = new StudentService(studentRepository, codingProfileRepository,
                performanceRepository, usernameUniquenessValidator,
                platformTransactionManager, autoSyncService);
    }

    private Student existingStudent() {
        return Student.builder()
                .id(UUID.fromString("00000000-0000-0000-0000-000000000001"))
                .rollNumber("21CS001")
                .name("Priya Sharma")
                .email("priya@college.edu")
                .build();
    }

    private UpdateStudentRequest request(String branch, String section) {
        return new UpdateStudentRequest("21CS001", "Priya Sharma", "priya@college.edu",
                branch, 3, section, "9999999999", null, null, "priya_lc", null, null);
    }

    @Test
    void updateRejectsUsernameOwnedByAnotherStudent() {
        when(studentRepository.findById(existingStudent().getId())).thenReturn(Optional.of(existingStudent()));
        org.mockito.Mockito.doThrow(new ApiException(HttpStatus.CONFLICT, "This Codeforces username is already registered."))
                .when(usernameUniquenessValidator).validate("priya_lc", "tourist", null, existingStudent().getId());

        UpdateStudentRequest req = new UpdateStudentRequest("21CS001", "Priya Sharma", "priya@college.edu",
                "CSE", 3, "A", "9999999999", null, null, "priya_lc", "tourist", null);

        assertThatThrownBy(() -> studentService.updateStudent(existingStudent().getId(), req))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.CONFLICT))
                .hasMessageContaining("already registered");

        verify(studentRepository, never()).save(any());
        verify(codingProfileRepository, never()).save(any());
    }

    @Test
    void updateAllowsKeepingOwnUsername() {
        UUID id = existingStudent().getId();
        when(studentRepository.findById(id)).thenReturn(Optional.of(existingStudent()));
        when(studentRepository.save(any(Student.class))).thenAnswer(inv -> inv.getArgument(0));
        when(codingProfileRepository.findByStudentId(id))
                .thenReturn(Optional.of(CodingProfile.builder().student(existingStudent())
                        .leetcodeUsername("priya_lc").build()));

        StudentResponse response = studentService.updateStudent(id, request("CSM", "C"));

        assertThat(response.branch()).isEqualTo("CSM");
        assertThat(response.section()).isEqualTo("C");

        verify(codingProfileRepository).save(profileCaptor.capture());
        assertThat(profileCaptor.getValue().getLeetcodeUsername()).isEqualTo("priya_lc");
    }

    @Test
    void createStudentNormalizesBranchAndSectionAndPersistsProfileAndPerformance() {
        CreateStudentRequest req = new CreateStudentRequest(
                "21CS004", "Ravi", "ravi@college.edu", "csm", 2, "c", null, null, null,
                "ravi_lc", null, "ravi_cc");
        when(studentRepository.save(any(Student.class))).thenAnswer(inv -> inv.getArgument(0));

        studentService.createStudent(req);

        verify(studentRepository).save(studentCaptor.capture());
        assertThat(studentCaptor.getValue().getBranch()).isEqualTo("CSM");
        assertThat(studentCaptor.getValue().getSection()).isEqualTo("C");

        verify(codingProfileRepository).save(profileCaptor.capture());
        assertThat(profileCaptor.getValue().getLeetcodeUsername()).isEqualTo("ravi_lc");
        assertThat(profileCaptor.getValue().getCodechefUsername()).isEqualTo("ravi_cc");
        assertThat(profileCaptor.getValue().getCodeforcesUsername()).isNull();

        verify(performanceRepository).save(performanceCaptor.capture());
        assertThat(performanceCaptor.getValue().getStudent()).isNotNull();
    }

    @Test
    void createStudentTriggersAutomaticSync() {
        CreateStudentRequest req = new CreateStudentRequest(
                "21CS005", "Ravi", "ravi@college.edu", "CSM", 2, "C", null, null, null, null, null, null);
        Student saved = Student.builder().id(UUID.randomUUID()).rollNumber("21CS005").build();
        when(studentRepository.save(any(Student.class))).thenReturn(saved);

        studentService.createStudent(req);

        verify(autoSyncService).syncStudentBestEffort(saved.getId());
    }

    @Test
    void createStudentRejectsUsernameOwnedByAnotherStudent() {
        CreateStudentRequest req = new CreateStudentRequest(
                "21CS006", "Ravi", "ravi@college.edu", "CSM", 2, "C", null, null, null, "sriram_9167", null, null);
        doThrow(new ApiException(HttpStatus.CONFLICT, "This LeetCode username is already registered."))
                .when(usernameUniquenessValidator).validate("sriram_9167", null, null, null);

        assertThatThrownBy(() -> studentService.createStudent(req))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.CONFLICT));

        verify(studentRepository, never()).save(any());
        verify(codingProfileRepository, never()).save(any());
    }

    @Test
    void createStudentTrimsPhone() {
        CreateStudentRequest req = new CreateStudentRequest(
                "21CS007", "Ravi", "ravi@college.edu", "CSM", 2, "C", " 9876543210 ",
                null, null, null, null, null);
        when(studentRepository.save(any(Student.class))).thenAnswer(inv -> inv.getArgument(0));

        studentService.createStudent(req);

        verify(studentRepository).save(studentCaptor.capture());
        assertThat(studentCaptor.getValue().getPhone()).isEqualTo("9876543210");
    }

    @Test
    void updateStudentTrimsPhone() {
        UUID id = existingStudent().getId();
        when(studentRepository.findById(id)).thenReturn(Optional.of(existingStudent()));
        when(studentRepository.save(any(Student.class))).thenAnswer(inv -> inv.getArgument(0));
        when(codingProfileRepository.findByStudentId(id)).thenReturn(Optional.empty());

        studentService.updateStudent(id, new UpdateStudentRequest(
                "21CS001", "Priya Sharma", "priya@college.edu", "CSM", 3, "C", " 9123456789 ",
                null, null, null, null, null));

        verify(studentRepository).save(studentCaptor.capture());
        assertThat(studentCaptor.getValue().getPhone()).isEqualTo("9123456789");
    }
}
