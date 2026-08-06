package com.codetrack.backend.service;

import com.codetrack.backend.dto.StudentResponse;
import com.codetrack.backend.dto.UpdateStudentRequest;
import com.codetrack.backend.entity.CodingProfile;
import com.codetrack.backend.entity.Student;
import com.codetrack.backend.exception.ApiException;
import com.codetrack.backend.repository.CodingProfileRepository;
import com.codetrack.backend.repository.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentServiceTest {

    @Mock private StudentRepository studentRepository;
    @Mock private CodingProfileRepository codingProfileRepository;
    @Mock private UsernameUniquenessValidator usernameUniquenessValidator;

    @Captor private ArgumentCaptor<Student> studentCaptor;
    @Captor private ArgumentCaptor<CodingProfile> profileCaptor;

    private StudentService studentService;

    @BeforeEach
    void setUp() {
        studentService = new StudentService(studentRepository, codingProfileRepository, usernameUniquenessValidator);
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
                branch, 3, section, "9999999999", "priya_lc", null, null);
    }

    @Test
    void updateRejectsUsernameOwnedByAnotherStudent() {
        when(studentRepository.findById(existingStudent().getId())).thenReturn(Optional.of(existingStudent()));
        org.mockito.Mockito.doThrow(new ApiException(HttpStatus.CONFLICT, "This Codeforces username is already registered."))
                .when(usernameUniquenessValidator).validate("priya_lc", "tourist", null, existingStudent().getId());

        UpdateStudentRequest req = new UpdateStudentRequest("21CS001", "Priya Sharma", "priya@college.edu",
                "CSE", 3, "A", "9999999999", "priya_lc", "tourist", null);

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
    void createStudentNormalizesBranchAndSection() {
        com.codetrack.backend.dto.CreateStudentRequest req = new com.codetrack.backend.dto.CreateStudentRequest(
                "21CS004", "Ravi", "ravi@college.edu", "csm", 2, "c", null);
        when(studentRepository.save(any(Student.class))).thenAnswer(inv -> inv.getArgument(0));

        studentService.createStudent(req);

        verify(studentRepository).save(studentCaptor.capture());
        assertThat(studentCaptor.getValue().getBranch()).isEqualTo("CSM");
        assertThat(studentCaptor.getValue().getSection()).isEqualTo("C");
    }
}
