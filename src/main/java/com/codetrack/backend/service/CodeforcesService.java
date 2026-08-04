package com.codetrack.backend.service;

import com.codetrack.backend.dto.PlatformData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class CodeforcesService {

    private static final String PLATFORM = "CODEFORCES";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.platforms.codeforces.api-url:https://codeforces.com/api/user.info}")
    private String apiUrl;

    @Value("${app.platforms.codeforces.rating-url:https://codeforces.com/api/user.rating}")
    private String ratingUrl;

    @Value("${app.platforms.codeforces.status-url:https://codeforces.com/api/user.status}")
    private String statusUrl;

    /**
     * Fetches a Codeforces user's rating/maxRating/rank/contestCount from the public API.
     * Returns empty if the handle does not exist or the API is unreachable. Problems
     * solved is derived from the submissions API (distinct AC problems), so it stays
     * null only when that call fails.
     */
    public Optional<PlatformData> fetch(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        try {
            String handle = username.trim();
            String url = apiUrl + "?handles=" + handle;
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return Optional.empty();
            }

            JsonNode root = objectMapper.readTree(response.getBody());
            if (!"OK".equals(root.path("status").asText())) {
                log.warn("Codeforces API error for '{}': {}", handle, root.path("comment").asText("unknown"));
                return Optional.empty();
            }

            JsonNode user = root.path("result").isArray() && root.path("result").size() > 0
                    ? root.path("result").get(0)
                    : null;
            if (user == null || user.isMissingNode()) {
                return Optional.empty();
            }

            Integer rating = user.path("rating").isMissingNode() ? null : user.path("rating").asInt();
            Integer maxRating = user.path("maxRating").isMissingNode() ? null : user.path("maxRating").asInt();
            String rank = user.path("rank").isMissingNode() ? null : user.path("rank").asText();

            return Optional.of(new PlatformData(PLATFORM, rating, maxRating, rank, fetchSolvedCount(handle), null, null, null, null,
                    fetchContestCount(handle), null));
        } catch (RestClientException | com.fasterxml.jackson.core.JsonProcessingException ex) {
            log.warn("Codeforces sync failed for '{}': {}", username, ex.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Counts distinct accepted problems from the user's submission history via
     * {@code user.status}. Omitting {@code from}/{@code count} makes Codeforces
     * return the whole submission history in a single response, which is faster
     * and lighter than paging and stays well inside the platform's request
     * budget (and Render's request timeout). Returns null if the call fails.
     */
    private Integer fetchSolvedCount(String handle) {
        Set<String> solved = new HashSet<>();
        try {
            String url = statusUrl + "?handle=" + handle;
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return null;
            }
            JsonNode root = objectMapper.readTree(response.getBody());
            if (!"OK".equals(root.path("status").asText()) || !root.path("result").isArray()) {
                return null;
            }
            for (JsonNode sub : root.path("result")) {
                if ("OK".equals(sub.path("verdict").asText())) {
                    String key = sub.path("problem").path("contestId").asText()
                            + "/" + sub.path("problem").path("index").asText();
                    solved.add(key);
                }
            }
        } catch (RestClientException | com.fasterxml.jackson.core.JsonProcessingException ex) {
            log.warn("Codeforces solved count failed for '{}': {}", handle, ex.getMessage());
        }
        return solved.isEmpty() ? null : solved.size();
    }

    private Integer fetchContestCount(String handle) {
        try {
            String url = ratingUrl + "?handle=" + handle;
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return null;
            }
            JsonNode root = objectMapper.readTree(response.getBody());
            if (!"OK".equals(root.path("status").asText()) || !root.path("result").isArray()) {
                return null;
            }
            return root.path("result").size();
        } catch (RestClientException | com.fasterxml.jackson.core.JsonProcessingException ex) {
            log.warn("Codeforces contest count failed for '{}': {}", handle, ex.getMessage());
            return null;
        }
    }
}