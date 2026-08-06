package com.codetrack.backend.service;

import com.codetrack.backend.dto.CsvImportResult;
import com.codetrack.backend.entity.Performance;
import com.codetrack.backend.entity.Student;
import com.codetrack.backend.repository.PerformanceRepository;
import com.codetrack.backend.repository.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CsvServiceTest {

    @Mock private StudentRepository studentRepository;
    @Mock private PerformanceRepository performanceRepository;

    private CsvService csvService;

    @BeforeEach
    void setUp() {
        csvService = new CsvService(studentRepository, performanceRepository);
    }

    @Test
    void importsValidRows() {
        String csv = "rollNumber,name,email,branch,year,section,phone\n"
                + "21CS001,Ada Lovelace,ada@example.com,CSE,3,A,9876543210\n"
                + "21CS002,Alan Turing,alan@example.com,CSE,3,B,9876543211\n";

        when(studentRepository.existsByRollNumberIgnoreCase(any())).thenReturn(false);
        when(studentRepository.existsByEmailIgnoreCase(any())).thenReturn(false);
        when(studentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CsvImportResult result = csvService.importStudents(
                new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));

        assertThat(result.imported()).isEqualTo(2);
        assertThat(result.failed()).isZero();
        verify(studentRepository, org.mockito.Mockito.times(2)).save(any());
    }

    @Test
    void reportsDuplicateRollNumberAsError() {
        String csv = "21CS001,Ada,ada@example.com,CSE,3,A,\n"
                + "21CS001,Alan,alan@example.com,CSE,3,B,\n";

        when(studentRepository.existsByRollNumberIgnoreCase("21CS001")).thenReturn(true);

        CsvImportResult result = csvService.importStudents(
                new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));

        assertThat(result.imported()).isZero();
        assertThat(result.failed()).isEqualTo(2);
        assertThat(result.errors()).hasSize(2);
        verify(studentRepository, never()).save(any());
    }

    @Test
    void skipsBlankLinesAndShortRows() {
        String csv = "21CS001,Ada,ada@example.com,CSE,3,A,9876543210\n\n21CS002\n";

        when(studentRepository.existsByRollNumberIgnoreCase("21CS001")).thenReturn(false);
        when(studentRepository.existsByEmailIgnoreCase("ada@example.com")).thenReturn(false);
        when(studentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CsvImportResult result = csvService.importStudents(
                new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));

        assertThat(result.imported()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.errors().get(0)).contains("at least roll number");
    }

    @Test
    void rejectsRowsWithoutPhoneOrWithInvalidPhone() {
        String csv = "21CS003,Ada,ada@example.com,CSE,3,A,\n"
                + "21CS004,Bob,bob@example.com,CSE,3,A,12345\n"
                + "21CS005,Cid,cid@example.com,CSE,3,A,9876543210\n";

        when(studentRepository.existsByRollNumberIgnoreCase(any())).thenReturn(false);
        when(studentRepository.existsByEmailIgnoreCase(any())).thenReturn(false);
        when(studentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CsvImportResult result = csvService.importStudents(
                new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));

        assertThat(result.imported()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(2);
        assertThat(result.errors()).anyMatch(e -> e.contains("Phone number is required"));
        assertThat(result.errors()).anyMatch(e -> e.contains("exactly 10 digits"));
        verify(studentRepository, org.mockito.Mockito.times(1)).save(any());
    }

    @Test
    void exportIncludesHeaderAndPerformanceColumns() {
        Student student = Student.builder()
                .id(UUID.randomUUID())
                .rollNumber("21CS001")
                .name("Ada Lovelace")
                .email("ada@example.com")
                .branch("CSE")
                .build();
        Performance performance = Performance.builder()
                .student(student)
                .overallScore(new BigDecimal("7.50"))
                .leetcodeSolved(120)
                .build();

        when(studentRepository.findAll()).thenReturn(List.of(student));
        when(performanceRepository.findAll()).thenReturn(List.of(performance));

        String csv = csvService.exportCsv();

        assertThat(csv).contains("rollNumber,name,email,branch,year,section,phone,overallScore,leetcodeSolved,codeforcesRating,codechefRating");
        assertThat(csv).contains("21CS001,Ada Lovelace,ada@example.com,CSE,,,");
        assertThat(csv).contains(",7.50,120,,");
    }
}
