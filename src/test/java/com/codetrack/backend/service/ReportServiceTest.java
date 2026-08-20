package com.codetrack.backend.service;

import com.codetrack.backend.entity.Performance;
import com.codetrack.backend.entity.Student;
import com.codetrack.backend.exception.ApiException;
import com.codetrack.backend.repository.CodingProfileRepository;
import com.codetrack.backend.repository.PerformanceRepository;
import com.codetrack.backend.repository.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock private StudentRepository studentRepository;
    @Mock private CodingProfileRepository codingProfileRepository;
    @Mock private PerformanceRepository performanceRepository;

    private ReportService reportService;

    @BeforeEach
    void setUp() {
        reportService = new ReportService(studentRepository, codingProfileRepository, performanceRepository);
    }

    @Test
    void sectionReportGeneratesPdfGroupedBySection() {
        Student a1 = student("1", "A", 3, "Alpha");
        Student a2 = student("2", "A", 3, "Beta");
        Student b1 = student("3", "B", 3, "Gamma");

        Performance pa1 = performance(a1, new BigDecimal("8.50"), 10, 20, 30);
        Performance pa2 = performance(a2, new BigDecimal("6.00"), 5, 5, 5);
        Performance pb1 = performance(b1, null, 1, 2, 3);

        when(studentRepository.findAll()).thenReturn(List.of(b1, a1, a2));
        when(performanceRepository.findAll()).thenReturn(List.of(pb1, pa1, pa2));

        byte[] pdf = reportService.generateSectionReport("3");

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, StandardCharsets.ISO_8859_1)).startsWith("%PDF");
    }

    @Test
    void sectionReportEmptyYearStillGeneratesPdf() {
        when(studentRepository.findAll()).thenReturn(List.of());
        when(performanceRepository.findAll()).thenReturn(List.of());

        byte[] pdf = reportService.generateSectionReport("2");

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, StandardCharsets.ISO_8859_1)).startsWith("%PDF");
    }

    @Test
    void sectionReportRejectsInvalidYear() {
        assertThatThrownBy(() -> reportService.generateSectionReport("0"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("1 and 4");
        assertThatThrownBy(() -> reportService.generateSectionReport("5"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("1 and 4");
        assertThatThrownBy(() -> reportService.generateSectionReport("abc"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("1 and 4");
    }

    private Student student(String roll, String section, int year, String name) {
        return Student.builder()
                .id(UUID.randomUUID())
                .rollNumber(roll)
                .name(name)
                .branch("CSE")
                .year(year)
                .section(section)
                .build();
    }

    private Performance performance(Student student, BigDecimal score, Integer lc, Integer cf, Integer cc) {
        return Performance.builder()
                .student(student)
                .overallScore(score)
                .consistencyScore(new BigDecimal("5.00"))
                .leetcodeSolved(lc)
                .codeforcesSolved(cf)
                .codechefSolved(cc)
                .lastUpdated(Instant.now())
                .build();
    }
}