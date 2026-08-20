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
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CodeChef has no official public JSON API. The site's own ratings
 * endpoint (used by its leaderboard) is tried first, with an HTML scrape
 * of the user profile page as a fallback. All failures degrade gracefully
 * to {@code Optional.empty()} so one platform never breaks the sync-all.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CodeChefService {

    private static final String PLATFORM = "CODECHEF";

    private static final Pattern RATING_PATTERN = Pattern.compile("class=\"rating-number\">\\s*(\\d{2,5})");
    private static final Pattern STAR_DIV_PATTERN = Pattern.compile("<div class=\"rating-star\">(.*?)</div>", Pattern.DOTALL);
    private static final Pattern SOLVED_PATTERN = Pattern.compile(
            "<h3>\\s*Total Problems Solved:\\s*(\\d+)\\s*</h3>", Pattern.CASE_INSENSITIVE);
    private static final Pattern NOT_FOUND_PATTERN = Pattern.compile(
            "does not exist in our database", Pattern.CASE_INSENSITIVE);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.platforms.codechef.ratings-url:https://www.codechef.com/api/ratings/all}")
    private String ratingsUrl;

    @Value("${app.platforms.codechef.profile-url:https://www.codechef.com/users/}")
    private String profileUrl;

    public Optional<PlatformData> fetch(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        String handle = username.trim();

        Optional<PlatformData> viaApi = fetchFromRatingsApi(handle);
        Optional<PlatformData> viaProfile = fetchFromProfilePage(handle);
        if (viaApi.isEmpty() && viaProfile.isEmpty()) {
            return Optional.empty();
        }

        PlatformData api = viaApi.orElse(null);
        PlatformData profile = viaProfile.orElse(null);
        return Optional.of(new PlatformData(
                PLATFORM,
                firstNonNull(PlatformData::rating, api, profile),
                null,
                null,
                firstNonNull(PlatformData::problemsSolved, profile, api),
                null,
                null,
                null,
                firstNonNull(PlatformData::globalRanking, api, profile),
                null,
                firstNonNull(PlatformData::stars, api, profile)
        ));
    }

    private static <T> T firstNonNull(Function<PlatformData, T> getter, PlatformData... sources) {
        for (PlatformData source : sources) {
            if (source != null) {
                T value = getter.apply(source);
                if (value != null) {
                    return value;
                }
            }
        }
        return null;
    }

    private Optional<PlatformData> fetchFromRatingsApi(String handle) {
        try {
            String url = ratingsUrl + "?filterBy=" + handle;
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, entityWithBrowserHeaders(), String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return Optional.empty();
            }

            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode all = root.path("all");
            if (!root.path("success").asBoolean(false) || !all.isArray()) {
                return Optional.empty();
            }

            for (JsonNode entry : all) {
                if (handle.equalsIgnoreCase(entry.path("handle").asText())) {
                    Integer rating = entry.path("rating").isMissingNode() ? null : entry.path("rating").asInt();
                    String stars = entry.path("stars").isMissingNode() ? null : entry.path("stars").asText();
                    Integer globalRank = entry.path("global_rank").isMissingNode() ? null : entry.path("global_rank").asInt();
                    return Optional.of(new PlatformData(PLATFORM, rating, null, null, null, null, null, null, globalRank, null, stars));
                }
            }
            return Optional.empty();
        } catch (RestClientException | com.fasterxml.jackson.core.JsonProcessingException ex) {
            log.warn("CodeChef ratings API failed for '{}': {}", handle, ex.getMessage());
            return Optional.empty();
        }
    }

    private Optional<PlatformData> fetchFromProfilePage(String handle) {
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    profileUrl + handle, HttpMethod.GET, entityWithBrowserHeaders(), String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return Optional.empty();
            }
            String html = response.getBody();

            if (pageRepresentsMissingUser(html, handle)) {
                return Optional.empty();
            }

            Matcher ratingMatcher = RATING_PATTERN.matcher(html);
            Integer rating = ratingMatcher.find() ? Integer.parseInt(ratingMatcher.group(1)) : null;

            String stars = null;
            Matcher starDiv = STAR_DIV_PATTERN.matcher(html);
            if (starDiv.find()) {
                int count = starDiv.group(1).split("&#9733;", -1).length - 1;
                if (count > 0) {
                    stars = count + "★";
                }
            }

            Integer solved = null;
            Matcher solvedMatcher = SOLVED_PATTERN.matcher(html);
            if (solvedMatcher.find()) {
                solved = Integer.parseInt(solvedMatcher.group(1));
            }

            return Optional.of(new PlatformData(PLATFORM, rating, null, null, solved, null, null, null, null, null, stars));
        } catch (RestClientException | NumberFormatException ex) {
            log.warn("CodeChef profile scrape failed for '{}': {}", handle, ex.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Decides whether a CodeChef page actually represents an existing account.
     * Existence is determined from the page content itself, not from optional
     * statistics (rating, contests, recent activity). A profile is treated as
     * missing only when CodeChef explicitly reports the user as not found or the
     * page does not reference the handle at all. This keeps valid new or inactive
     * profiles (e.g. no rating, no contests) from being rejected.
     */
    private boolean pageRepresentsMissingUser(String html, String handle) {
        if (NOT_FOUND_PATTERN.matcher(html).find()) {
            return true;
        }
        return !html.toLowerCase().contains(handle.toLowerCase());
    }

    private HttpEntity<Void> entityWithBrowserHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.USER_AGENT, browserUserAgent());
        headers.set(HttpHeaders.REFERER, "https://www.codechef.com/");
        return new HttpEntity<>(headers);
    }

    private String browserUserAgent() {
        return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                + "(KHTML, like Gecko) Chrome/120.0 Safari/537.36";
    }
}