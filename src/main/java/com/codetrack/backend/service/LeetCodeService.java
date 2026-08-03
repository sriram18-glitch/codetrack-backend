package com.codetrack.backend.service;

import com.codetrack.backend.dto.PlatformData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LeetCode has no official public API. The community-standard GraphQL
 * endpoint is used, as the project spec describes. LeetCode's bot
 * protection can require a CSRF cookie: we fetch the site once to obtain the
 * {@code csrftoken} cookie, then replay it with an {@code x-csrftoken}
 * header on the GraphQL call. The CSRF lookup is optional — the GraphQL
 * endpoint still works without it, so a failed lookup (e.g. the homepage
 * returning 403) is logged as a warning and the sync continues header-less.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LeetCodeService {

    private static final String PLATFORM = "LEETCODE";

    private static final Pattern CSRF_PATTERN = Pattern.compile("csrftoken=([^;]+)");

    private static final String QUERY = """
            query userPublicProfile($username: String!) {
              matchedUser(username: $username) {
                username
                profile { ranking }
                submitStats: submitStatsGlobal {
                  acSubmissionNum { difficulty count }
                }
              }
              userContestRanking(username: $username) { rating }
            }
            """;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.platforms.leetcode.home-url:https://leetcode.com/}")
    private String homeUrl;

    @Value("${app.platforms.leetcode.graphql-url:https://leetcode.com/graphql}")
    private String graphqlUrl;

    public Optional<PlatformData> fetch(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set(HttpHeaders.USER_AGENT, browserUserAgent());
            headers.set(HttpHeaders.REFERER, "https://leetcode.com/");
            headers.set(HttpHeaders.ORIGIN, "https://leetcode.com");

            // CSRF is best-effort: if the homepage is unreachable the GraphQL
            // call still works without it, so never fail the sync here.
            String csrfToken = fetchCsrfToken();
            if (csrfToken == null) {
                log.warn("LeetCode: could not obtain csrf token; continuing without CSRF headers");
            } else {
                headers.set(HttpHeaders.COOKIE, "csrftoken=" + csrfToken);
                headers.set("x-csrftoken", csrfToken);
            }

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(
                    Map.of("query", QUERY, "variables", Map.of("username", username.trim())),
                    headers
            );

            ResponseEntity<String> response = restTemplate.postForEntity(graphqlUrl, request, String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return Optional.empty();
            }

            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode data = root.path("data");
            JsonNode matched = data.path("matchedUser");
            if (matched.isMissingNode() || matched.isNull()) {
                return Optional.empty();
            }

            Integer globalRanking = matched.path("profile").path("ranking").isMissingNode()
                    ? null : matched.path("profile").path("ranking").asInt();
            Integer rating = data.path("userContestRanking").path("rating").isMissingNode()
                    ? null : data.path("userContestRanking").path("rating").asInt();

            int easy = 0, medium = 0, hard = 0;
            JsonNode ac = matched.path("submitStats").path("acSubmissionNum");
            if (ac.isArray()) {
                for (JsonNode entry : ac) {
                    switch (entry.path("difficulty").asText()) {
                        case "Easy" -> easy = entry.path("count").asInt();
                        case "Medium" -> medium = entry.path("count").asInt();
                        case "Hard" -> hard = entry.path("count").asInt();
                        default -> { /* "All" handled below */ }
                    }
                }
            }
            int solved = matched.path("submitStats").path("acSubmissionNum").path(0).path("count").asInt();

            return Optional.of(new PlatformData(
                    PLATFORM, rating, null, null, solved, easy, medium, hard, globalRanking, null, null
            ));
        } catch (RestClientException | com.fasterxml.jackson.core.JsonProcessingException ex) {
            log.warn("LeetCode sync failed for '{}': {}", username, ex.getMessage());
            return Optional.empty();
        }
    }

    private String fetchCsrfToken() {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.USER_AGENT, browserUserAgent());
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<Void> response = restTemplate.exchange(homeUrl, HttpMethod.GET, entity, Void.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                return null;
            }
            for (String setCookie : response.getHeaders().get(HttpHeaders.SET_COOKIE)) {
                Matcher matcher = CSRF_PATTERN.matcher(setCookie);
                if (matcher.find()) {
                    return matcher.group(1);
                }
            }
            return null;
        } catch (Exception ex) {
            log.warn("LeetCode: csrf token lookup failed ({}); continuing without CSRF headers", ex.getMessage());
            return null;
        }
    }

    private String browserUserAgent() {
        return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                + "(KHTML, like Gecko) Chrome/120.0 Safari/537.36";
    }
}
