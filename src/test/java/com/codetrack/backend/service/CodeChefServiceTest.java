package com.codetrack.backend.service;

import com.codetrack.backend.dto.PlatformData;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Field;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CodeChefServiceTest {

    private RestTemplate restTemplate;
    private CodeChefService service;

    @BeforeEach
    void setUp() throws Exception {
        restTemplate = mock(RestTemplate.class);
        service = new CodeChefService(restTemplate, new ObjectMapper());
        setField("ratingsUrl", "https://www.codechef.com/api/ratings/all");
        setField("profileUrl", "https://www.codechef.com/users/");
    }

    private void setField(String name, String value) throws Exception {
        Field field = CodeChefService.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(service, value);
    }

    private void stubRatingsApi(String json) {
        when(restTemplate.exchange(contains("/api/ratings/all"), any(HttpMethod.class), any(HttpEntity.class),
                eq(String.class))).thenReturn(ResponseEntity.ok(json));
    }

    private void stubProfilePage(String html) {
        when(restTemplate.exchange(contains("/users/"), any(HttpMethod.class), any(HttpEntity.class),
                eq(String.class))).thenReturn(ResponseEntity.ok(html));
    }

    @Test
    void inactiveProfileWithoutRatingIsAccepted() {
        stubRatingsApi("{\"success\": true, \"all\": []}");
        stubProfilePage("<html><title>laxman_08 | CodeChef User Profile</title>"
                + "<div class=\"user-details-container\">laxman_08</div>"
                + "<h3>Total Problems Solved: 14</h3></html>");

        Optional<PlatformData> result = service.fetch("laxman_08");

        assertThat(result).isPresent();
        assertThat(result.get().rating()).isNull();
        assertThat(result.get().problemsSolved()).isEqualTo(14);
    }

    @Test
    void activeProfileWithRatingIsAccepted() {
        stubRatingsApi("{\"success\": true, \"all\": []}");
        stubProfilePage("<html><title>active_user | CodeChef User Profile</title>"
                + "<div class=\"rating-number\">1750</div>"
                + "<h3>Total Problems Solved: 100</h3></html>");

        Optional<PlatformData> result = service.fetch("active_user");

        assertThat(result).isPresent();
        assertThat(result.get().rating()).isEqualTo(1750);
        assertThat(result.get().problemsSolved()).isEqualTo(100);
    }

    @Test
    void profileFoundViaRatingsApiIsAcceptedEvenIfProfilePageFails() {
        when(restTemplate.exchange(contains("/api/ratings/all"), any(HttpMethod.class), any(HttpEntity.class),
                eq(String.class))).thenReturn(ResponseEntity.ok(
                        "{\"success\": true, \"all\": [{\"handle\": \"active_user\", \"rating\": 1750,"
                                + " \"stars\": \"2\", \"global_rank\": 100}]}"));
        when(restTemplate.exchange(contains("/users/"), any(HttpMethod.class), any(HttpEntity.class),
                eq(String.class))).thenThrow(new RestClientException("profile page down"));

        Optional<PlatformData> result = service.fetch("active_user");

        assertThat(result).isPresent();
        assertThat(result.get().rating()).isEqualTo(1750);
    }

    @Test
    void missingUserIsRejected() {
        stubRatingsApi("{\"success\": true, \"all\": []}");
        stubProfilePage("<html><body><script>showDrupalMessageModal("
                + "'The username specified does not exist in our database.');</script></body></html>");

        assertThat(service.fetch("thisuserdoesnotexistxyz12345")).isEmpty();
    }

    @Test
    void pageWithoutHandleIsRejected() {
        stubRatingsApi("{\"success\": true, \"all\": []}");
        stubProfilePage("<html><body>some unrelated error page</body></html>");

        assertThat(service.fetch("ghost_user")).isEmpty();
    }
}