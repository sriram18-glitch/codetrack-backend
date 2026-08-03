package com.codetrack.backend.service;

import com.codetrack.backend.dto.Insight;
import com.codetrack.backend.dto.InsightResponse;
import com.codetrack.backend.entity.CodingProfile;
import com.codetrack.backend.entity.Performance;
import com.codetrack.backend.entity.Student;
import com.codetrack.backend.exception.ApiException;
import com.codetrack.backend.repository.CodingProfileRepository;
import com.codetrack.backend.repository.PerformanceRepository;
import com.codetrack.backend.repository.StudentRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Generates coaching insights per student. Uses Google's Gemini model when
 * GEMINI_API_KEY is configured; otherwise falls back to deterministic,
 * rule-based insights so the feature works out of the box.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InsightService {

    private static final String MODEL = "gemini-1.5-flash";

    private final StudentRepository studentRepository;
    private final CodingProfileRepository codingProfileRepository;
    private final PerformanceRepository performanceRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Setter
    @Value("${app.ai.gemini-api-key:}")
    private String geminiApiKey;

    @Transactional(readOnly = true)
    public InsightResponse generate(UUID studentId) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Student not found"));

        CodingProfile profile = codingProfileRepository.findByStudentId(studentId).orElse(null);
        Performance performance = performanceRepository.findByStudentId(studentId).orElse(null);

        if (performance == null || performance.getOverallScore() == null) {
            List<Insight> insights = new ArrayList<>();
            insights.add(new Insight("INFO", "Sync this student's platforms first to generate insights."));
            if (profile == null || hasNoUsernames(profile)) {
                insights.add(new Insight("WARNING", "Attach at least one platform username to begin tracking."));
            }
            return new InsightResponse(student.getId(), student.getName(), null, insights);
        }

        List<Insight> insights = !geminiApiKey.isBlank()
                ? generateViaGemini(student, profile, performance)
                : ruleBased(student, profile, performance);

        return new InsightResponse(student.getId(), student.getName(), performance.getOverallScore(), insights);
    }

    private List<Insight> ruleBased(Student student, CodingProfile profile, Performance p) {
        List<Insight> insights = new ArrayList<>();
        double score = p.getOverallScore() == null ? 0 : p.getOverallScore().doubleValue();

        if (score >= 8.0) {
            insights.add(new Insight("SUCCESS", "Excellent overall coding readiness (" + p.getOverallScore() + "/10). Keep up the momentum."));
        } else if (score >= 6.0) {
            insights.add(new Insight("INFO", "Solid foundation (" + p.getOverallScore() + "/10). Pushing into harder problems will move you up fast."));
        } else if (score >= 4.0) {
            insights.add(new Insight("WARNING", "Developing profile (" + p.getOverallScore() + "/10). Consistency and contest participation are the biggest levers now."));
        } else {
            insights.add(new Insight("CRITICAL", "At-risk profile (" + p.getOverallScore() + "/10). Set a daily problem-solving goal to build the habit."));
        }

        if (p.getLeetcodeSolved() != null && p.getLeetcodeSolved() > 0) {
            if (p.getLeetcodeHard() != null && p.getLeetcodeHard() >= 20) {
                insights.add(new Insight("SUCCESS", "Strong LeetCode mix — " + p.getLeetcodeHard() + " hard problems solved. Contest ratings will thank you."));
            } else if (p.getLeetcodeSolved() < 50) {
                insights.add(new Insight("INFO", "LeetCode momentum is early (" + p.getLeetcodeSolved() + " solved). Solve 5 problems/day to hit 100 in a month."));
            }
        } else if (profile != null && profile.getLeetcodeUsername() != null) {
            insights.add(new Insight("WARNING", "LeetCode username is set but no data synced yet — run a sync to start tracking."));
        }

        if (p.getCodeforcesRating() != null) {
            int cf = p.getCodeforcesRating();
            if (cf >= 1900) {
                insights.add(new Insight("SUCCESS", "Codeforces rating " + cf + " — Candidate Master territory. Excellent contest performance."));
            } else if (cf >= 1400) {
                insights.add(new Insight("INFO", "Codeforces rating " + cf + ". Focus on Div2 B/C to cross Specialist."));
            } else {
                insights.add(new Insight("INFO", "Codeforces rating " + cf + ". Join weekly contests and upsolve one problem after each."));
            }
        } else {
            insights.add(new Insight("WARNING", "No Codeforces contest rating — participating in contests builds consistency."));
        }

        if (p.getCodechefRating() != null) {
            int cc = p.getCodechefRating();
            if (cc >= 1800) {
                insights.add(new Insight("SUCCESS", "CodeChef rating " + cc + " — strong long-contest performer."));
            } else {
                insights.add(new Insight("INFO", "CodeChef rating " + cc + ". Long challenges are great for building depth."));
            }
        }

        if (p.getConsistencyScore() != null && p.getConsistencyScore().doubleValue() < 4.0) {
            insights.add(new Insight("CRITICAL", "Consistency is low (" + p.getConsistencyScore() + "/10). Sync weekly and solve a little every day."));
        } else if (p.getConsistencyScore() != null) {
            insights.add(new Insight("SUCCESS", "Good consistency (" + p.getConsistencyScore() + "/10). Maintaining this is your best edge."));
        }

        return insights;
    }

    private List<Insight> generateViaGemini(Student student, CodingProfile profile, Performance p) {
        String prompt = buildPrompt(student, profile, p);
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(
                    Map.of("contents", List.of(Map.of("parts", List.of(Map.of("text", prompt))))),
                    headers
            );
            String url = "https://generativelanguage.googleapis.com/v1beta/models/" + MODEL
                    + ":generateContent?key=" + geminiApiKey;

            String body = restTemplate.postForObject(url, request, String.class);
            if (body == null) return ruleBased(student, profile, p);

            JsonNode root = objectMapper.readTree(body);
            String text = root.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText("");
            if (text.isBlank()) return ruleBased(student, profile, p);

            List<Insight> insights = new ArrayList<>();
            for (String line : text.split("\\n")) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("**")) continue;
                trimmed = trimmed.replaceFirst("^[-*]\\s*", "");
                String severity = trimmed.toLowerCase().contains("excellent") || trimmed.toLowerCase().contains("strong")
                        ? "SUCCESS" : "INFO";
                insights.add(new Insight(severity, trimmed));
            }
            return insights.isEmpty() ? ruleBased(student, profile, p) : insights;
        } catch (RestClientException | com.fasterxml.jackson.core.JsonProcessingException ex) {
            log.warn("Gemini insight generation failed, falling back to rules: {}", ex.getMessage());
            return ruleBased(student, profile, p);
        }
    }

    private String buildPrompt(Student student, CodingProfile profile, Performance p) {
        return """
                You are a placement-coach AI. Given a college student's competitive programming
                profile, produce 3-5 short actionable coaching insights. Be specific (topics, weekly
                contest advice, daily problem goals). Return plain lines, no markdown.

                Student: %s (%s)
                Overall score: %s/10   Consistency: %s/10
                LeetCode: rating=%s, solved=%s (easy=%s, medium=%s, hard=%s)
                Codeforces rating: %s
                CodeChef rating: %s
                Platforms configured: LeetCode=%s, Codeforces=%s, CodeChef=%s
                """.formatted(
                student.getName(), student.getRollNumber(),
                Optional.ofNullable(p.getOverallScore()).orElse(java.math.BigDecimal.ZERO),
                Optional.ofNullable(p.getConsistencyScore()).orElse(java.math.BigDecimal.ZERO),
                p.getLeetcodeRating(), p.getLeetcodeSolved(), p.getLeetcodeEasy(),
                p.getLeetcodeMedium(), p.getLeetcodeHard(),
                p.getCodeforcesRating(), p.getCodechefRating(),
                profile == null ? "no" : profile.getLeetcodeUsername(),
                profile == null ? "no" : profile.getCodeforcesUsername(),
                profile == null ? "no" : profile.getCodechefUsername()
        );
    }

    private boolean hasNoUsernames(CodingProfile profile) {
        return profile.getLeetcodeUsername() == null
                && profile.getCodeforcesUsername() == null
                && profile.getCodechefUsername() == null;
    }
}
