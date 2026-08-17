package com.codetrack.backend.service;

import com.codetrack.backend.dto.PlatformData;
import com.codetrack.backend.dto.RegisterRequest;
import com.codetrack.backend.dto.RegisterResponse;
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
class RegistrationServiceTest {

    @Mock private StudentRepository studentRepository;
    @Mock private CodingProfileRepository codingProfileRepository;
    @Mock private PerformanceRepository performanceRepository;
    @Mock private LeetCodeService leetCodeService;
    @Mock private CodeforcesService codeforcesService;
    @Mock private CodeChefService codeChefService;
    @Mock private UsernameUniquenessValidator usernameUniquenessValidator;
    @Mock private PlatformTransactionManager platformTransactionManager;
    @Mock private AutoSyncService autoSyncService;

    @Captor private ArgumentCaptor<Student> studentCaptor;
    @Captor private ArgumentCaptor<CodingProfile> profileCaptor;
    @Captor private ArgumentCaptor<Performance> performanceCaptor;

    private RegistrationService registrationService;

    @BeforeEach
    void setUp() {
        registrationService = new RegistrationService(studentRepository, codingProfileRepository,
                performanceRepository, leetCodeService, codeforcesService, codeChefService,
                usernameUniquenessValidator, platformTransactionManager, autoSyncService);
    }

    private RegisterRequest validRequest() {
        return new RegisterRequest("21CS001", "Priya Sharma", "priya@college.edu",
                "CSE", 3, "A", "9999999999",
                "https://github.com/priya", "https://www.linkedin.com/in/priya",
                "priya_lc", "priya_cf", "priya_cc");
    }

    @Test
    void duplicateRollNumberIsRejected() {
        when(studentRepository.existsByRollNumberIgnoreCase("21CS001")).thenReturn(true);

        assertThatThrownBy(() -> registrationService.register(validRequest()))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.CONFLICT))
                .hasMessageContaining("roll number already exists");

        verify(studentRepository, never()).save(any());
        verify(codingProfileRepository, never()).save(any());
        verify(performanceRepository, never()).save(any());
    }

    @Test
    void duplicateEmailIsRejected() {
        when(studentRepository.existsByRollNumberIgnoreCase("21CS001")).thenReturn(false);
        when(studentRepository.existsByEmailIgnoreCase("priya@college.edu")).thenReturn(true);

        assertThatThrownBy(() -> registrationService.register(validRequest()))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.CONFLICT))
                .hasMessageContaining("email already exists");

        verify(studentRepository, never()).save(any());
    }

    @Test
    void invalidUsernameIsRejected() {
        when(studentRepository.existsByRollNumberIgnoreCase("21CS001")).thenReturn(false);
        when(studentRepository.existsByEmailIgnoreCase("priya@college.edu")).thenReturn(false);
        when(leetCodeService.fetch("priya_lc")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> registrationService.register(validRequest()))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST))
                .hasMessageContaining("LeetCode username 'priya_lc' does not exist");

        verify(studentRepository, never()).save(any());
    }

    @Test
    void invalidCodeforcesUsernameIsRejected() {
        RegisterRequest request = validRequest();
        when(studentRepository.existsByRollNumberIgnoreCase("21CS001")).thenReturn(false);
        when(studentRepository.existsByEmailIgnoreCase("priya@college.edu")).thenReturn(false);
        when(leetCodeService.fetch("priya_lc"))
                .thenReturn(Optional.of(platform("LEETCODE", 1500, 100)));
        when(codeforcesService.fetch("priya_cf")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> registrationService.register(request))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Codeforces username 'priya_cf' does not exist");
    }

    @Test
    void successfulRegistrationCreatesStudentProfileAndPerformance() {
        when(studentRepository.existsByRollNumberIgnoreCase("21CS001")).thenReturn(false);
        when(studentRepository.existsByEmailIgnoreCase("priya@college.edu")).thenReturn(false);
        when(leetCodeService.fetch("priya_lc")).thenReturn(Optional.of(platform("LEETCODE", 1500, 100)));
        when(codeforcesService.fetch("priya_cf")).thenReturn(Optional.of(platform("CODEFORCES", 1600, 50)));
        when(codeChefService.fetch("priya_cc")).thenReturn(Optional.of(platform("CODECHEF", 1700, 40)));

        Student saved = Student.builder()
                .id(UUID.randomUUID())
                .rollNumber("21CS001")
                .name("Priya Sharma")
                .email("priya@college.edu")
                .build();
        when(studentRepository.save(any(Student.class))).thenReturn(saved);

        RegisterResponse response = registrationService.register(validRequest());

        assertThat(response.studentId()).isEqualTo(saved.getId());
        assertThat(response.message()).contains("successful");

        verify(studentRepository).save(studentCaptor.capture());
        Student created = studentCaptor.getValue();
        assertThat(created.getRollNumber()).isEqualTo("21CS001");
        assertThat(created.getEmail()).isEqualTo("priya@college.edu");
        assertThat(created.getBranch()).isEqualTo("CSE");
        assertThat(created.getYear()).isEqualTo(3);
        assertThat(created.getSection()).isEqualTo("A");
        assertThat(created.getPhone()).isEqualTo("9999999999");

        verify(codingProfileRepository).save(profileCaptor.capture());
        CodingProfile profile = profileCaptor.getValue();
        assertThat(profile.getLeetcodeUsername()).isEqualTo("priya_lc");
        assertThat(profile.getCodeforcesUsername()).isEqualTo("priya_cf");
        assertThat(profile.getCodechefUsername()).isEqualTo("priya_cc");

        verify(performanceRepository).save(performanceCaptor.capture());
        assertThat(performanceCaptor.getValue().getStudent()).isNotNull();
    }

    @Test
    void blankUsernamesAreStoredAsNullWithoutValidation() {
        RegisterRequest request = new RegisterRequest("21CS002", "Ravi", "ravi@college.edu",
                "ECE", 2, "B", null, null, null, "  ", "", null);

        when(studentRepository.existsByRollNumberIgnoreCase("21CS002")).thenReturn(false);
        when(studentRepository.existsByEmailIgnoreCase("ravi@college.edu")).thenReturn(false);
        Student saved = Student.builder().id(UUID.randomUUID()).build();
        when(studentRepository.save(any(Student.class))).thenReturn(saved);

        registrationService.register(request);

        verify(leetCodeService, never()).fetch(any());
        verify(codeforcesService, never()).fetch(any());
        verify(codeChefService, never()).fetch(any());

        verify(codingProfileRepository).save(profileCaptor.capture());
        assertThat(profileCaptor.getValue().getLeetcodeUsername()).isNull();
        assertThat(profileCaptor.getValue().getCodeforcesUsername()).isNull();
        assertThat(profileCaptor.getValue().getCodechefUsername()).isNull();
    }

    @Test
    void validateUsernameReturnsTrueWhenUserExists() {
        when(leetCodeService.fetch("sriram_9167")).thenReturn(Optional.of(platform("LEETCODE", 1500, 100)));

        assertThat(registrationService.validateUsername("leetcode", "sriram_9167")).isTrue();
        assertThat(registrationService.validateUsername("LC", "sriram_9167")).isTrue();
    }

    @Test
    void validateUsernameReturnsFalseForUnknownUser() {
        when(leetCodeService.fetch("does_not_exist_xyz")).thenReturn(Optional.empty());

        assertThat(registrationService.validateUsername("leetcode", "does_not_exist_xyz")).isFalse();
    }

    @Test
    void alreadyRegisteredUsernameIsRejected() {
        when(studentRepository.existsByRollNumberIgnoreCase("21CS001")).thenReturn(false);
        when(studentRepository.existsByEmailIgnoreCase("priya@college.edu")).thenReturn(false);
        when(leetCodeService.fetch("priya_lc")).thenReturn(Optional.of(platform("LEETCODE", 1500, 100)));
        when(codeforcesService.fetch("priya_cf")).thenReturn(Optional.of(platform("CODEFORCES", 1600, 50)));
        when(codeChefService.fetch("priya_cc")).thenReturn(Optional.of(platform("CODECHEF", 1700, 40)));
        org.mockito.Mockito.doThrow(new ApiException(HttpStatus.CONFLICT, "This LeetCode username is already registered."))
                .when(usernameUniquenessValidator).validate("priya_lc", "priya_cf", "priya_cc", null);

        assertThatThrownBy(() -> registrationService.register(validRequest()))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.CONFLICT))
                .hasMessageContaining("already registered");

        verify(studentRepository, never()).save(any());
    }

    @Test
    void branchAndSectionAreNormalizedToUpperCase() {
        RegisterRequest request = new RegisterRequest("21CS003", "Neha", "neha@college.edu",
                "csm", 2, "c", null, null, null, null, null, null);

        when(studentRepository.existsByRollNumberIgnoreCase("21CS003")).thenReturn(false);
        when(studentRepository.existsByEmailIgnoreCase("neha@college.edu")).thenReturn(false);
        Student saved = Student.builder().id(UUID.randomUUID()).build();
        when(studentRepository.save(any(Student.class))).thenReturn(saved);

        registrationService.register(request);

        verify(studentRepository).save(studentCaptor.capture());
        assertThat(studentCaptor.getValue().getBranch()).isEqualTo("CSM");
        assertThat(studentCaptor.getValue().getSection()).isEqualTo("C");
    }

    @Test
    void successfulRegistrationTriggersAutomaticSync() {
        when(studentRepository.existsByRollNumberIgnoreCase("21CS001")).thenReturn(false);
        when(studentRepository.existsByEmailIgnoreCase("priya@college.edu")).thenReturn(false);
        when(leetCodeService.fetch("priya_lc")).thenReturn(Optional.of(platform("LEETCODE", 1500, 100)));
        when(codeforcesService.fetch("priya_cf")).thenReturn(Optional.of(platform("CODEFORCES", 1600, 50)));
        when(codeChefService.fetch("priya_cc")).thenReturn(Optional.of(platform("CODECHEF", 1700, 40)));
        Student saved = Student.builder().id(UUID.randomUUID()).rollNumber("21CS001").name("Priya Sharma").build();
        when(studentRepository.save(any(Student.class))).thenReturn(saved);

        registrationService.register(validRequest());

        verify(autoSyncService).syncStudentBestEffort(saved.getId());
    }

    @Test
    void registrationStillSucceedsWhenAutomaticSyncThrows() {
        when(studentRepository.existsByRollNumberIgnoreCase("21CS001")).thenReturn(false);
        when(studentRepository.existsByEmailIgnoreCase("priya@college.edu")).thenReturn(false);
        when(leetCodeService.fetch("priya_lc")).thenReturn(Optional.of(platform("LEETCODE", 1500, 100)));
        when(codeforcesService.fetch("priya_cf")).thenReturn(Optional.of(platform("CODEFORCES", 1600, 50)));
        when(codeChefService.fetch("priya_cc")).thenReturn(Optional.of(platform("CODECHEF", 1700, 40)));
        Student saved = Student.builder().id(UUID.randomUUID()).rollNumber("21CS001").name("Priya Sharma").build();
        when(studentRepository.save(any(Student.class))).thenReturn(saved);
        doThrow(new RuntimeException("platform down")).when(autoSyncService).syncStudentBestEffort(saved.getId());

        RegisterResponse response = registrationService.register(validRequest());

        assertThat(response.studentId()).isEqualTo(saved.getId());
    }

    private PlatformData platform(String platform, int rating, int solved) {
        return new PlatformData(platform, rating, null, null, solved, null, null, null, null, null, null);
    }
}
