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

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CodeforcesService {

    private static final String PLATFORM = "CODEFORCES";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.platforms.codeforces.api-url:https://codeforces.com/api/user.info}")
    private String apiUrl;

    /**
     * Fetches a Codeforces user's rating/maxRating/rank from the public API.
     * Returns empty if the handle does not exist or the API is unreachable.
     */
    public Optional<PlatformData> fetch(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        try {
            String url = apiUrl + "?handles=" + username.trim();
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return Optional.empty();
            }

            JsonNode root = objectMapper.readTree(response.getBody());
            if (!"OK".equals(root.path("status").asText())) {
                log.warn("Codeforces API error for '{}': {}", username, root.path("comment").asText("unknown"));
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

            return Optional.of(new PlatformData(PLATFORM, rating, maxRating, rank, null, null, null, null, null));
        } catch (RestClientException | com.fasterxml.jackson.core.JsonProcessingException ex) {
            log.warn("Codeforces sync failed for '{}': {}", username, ex.getMessage());
            return Optional.empty();
        }
    }
}
