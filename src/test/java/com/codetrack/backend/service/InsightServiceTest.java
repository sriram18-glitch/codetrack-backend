package com.codetrack.backend.service;

import com.codetrack.backend.dto.InsightResponse;
import com.codetrack.backend.entity.Performance;
import com.codetrack.backend.entity.Student;
import com.codetrack.backend.repository.CodingProfileRepository;
import com.codetrack.backend.repository.PerformanceRepository;
import com.codetrack.backend.repository.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InsightServiceTest {

    @Mock private StudentRepository studentRepository;
    @Mock private CodingProfileRepository codingProfileRepository;
    @Mock private PerformanceRepository performanceRepository;
    @Mock private RestTemplate restTemplate;

    private InsightService insightService;

    private Student student;
    private UUID studentId;

    @BeforeEach
    void setUp() {
        insightService = new InsightService(studentRepository, codingProfileRepository,
                performanceRepository, restTemplate, new com.fasterxml.jackson.databind.ObjectMapper());
        insightService.setGeminiApiKey("");
        studentId = UUID.randomUUID();
        student = Student.builder().id(studentId).rollNumber("21CS001").name("Ada Lovelace").build();
    }

    @Test
    void returnsInfoWhenNoPerformanceExists() {
        when(studentRepository.findById(studentId)).thenReturn(Optional.of(student));
        when(codingProfileRepository.findByStudentId(studentId)).thenReturn(Optional.empty());
        when(performanceRepository.findByStudentId(studentId)).thenReturn(Optional.empty());

        InsightResponse response = insightService.generate(studentId);

        assertThat(response.insights()).isNotEmpty();
        assertThat(response.insights().get(0).text()).contains("Sync this student's platforms first");
    }

    @Test
    void flagsAtRiskForLowScore() {
        Performance performance = Performance.builder()
                .student(student)
                .overallScore(new BigDecimal("2.50"))
                .consistencyScore(new BigDecimal("1.00"))
                .build();

        when(studentRepository.findById(studentId)).thenReturn(Optional.of(student));
        when(codingProfileRepository.findByStudentId(studentId)).thenReturn(Optional.empty());
        when(performanceRepository.findByStudentId(studentId)).thenReturn(Optional.of(performance));

        InsightResponse response = insightService.generate(studentId);

        assertThat(response.overallScore()).isEqualByComparingTo(new BigDecimal("2.50"));
        assertThat(response.insights()).anyMatch(i -> i.severity().equals("CRITICAL"));
        assertThat(response.insights()).anyMatch(i -> i.text().contains("At-risk"));
    }

    @Test
    void praisesStrongScore() {
        Performance performance = Performance.builder()
                .student(student)
                .overallScore(new BigDecimal("8.80"))
                .consistencyScore(new BigDecimal("8.00"))
                .build();

        when(studentRepository.findById(studentId)).thenReturn(Optional.of(student));
        when(codingProfileRepository.findByStudentId(studentId)).thenReturn(Optional.empty());
        when(performanceRepository.findByStudentId(studentId)).thenReturn(Optional.of(performance));

        InsightResponse response = insightService.generate(studentId);

        assertThat(response.insights()).anyMatch(i -> i.severity().equals("SUCCESS"));
    }
}
