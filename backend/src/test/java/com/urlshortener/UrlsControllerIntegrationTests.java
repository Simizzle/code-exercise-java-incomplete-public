package com.urlshortener;

import com.urlshortener.model.PaginatedResponse;
import com.urlshortener.model.ShortenUrlResponse;
import com.urlshortener.model.UrlListItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.http.HttpStatus.*;

@SpringBootTest(webEnvironment = RANDOM_PORT)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:sqlite::memory:",
        "spring.datasource.driver-class-name=org.sqlite.JDBC"
})
class UrlsControllerIntegrationTests {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DELETE FROM shortened_urls");
        jdbcTemplate.update(
                "INSERT INTO shortened_urls (alias, full_url, created_at) VALUES (?, ?, ?)",
                "redir",
                "https://target.com/",
                Instant.now().toString()
        );
    }

    private void insertUrl(String alias, String fullUrl, String createdAt) {
        jdbcTemplate.update(
                """
                INSERT INTO shortened_urls (alias, full_url, created_at)
                VALUES (?, ?, ?)
                """,
                alias,
                fullUrl,
                createdAt
        );
    }

    @Nested
    @DisplayName("Redirection behaviour tests")
    class RedirectionBehaviourTests {
        @Test
        void getAlias_ExistingAlias_Returns301WithLocation() {
            var response = restTemplate.getForEntity("/redir", String.class);

            assertThat(response.getStatusCode()).isEqualTo(MOVED_PERMANENTLY);
            assertThat(response.getHeaders().getLocation()).hasToString("https://target.com/");
            assertThat(response.getBody()).isNullOrEmpty();
        }

        @Test
        void getAlias_UnknownAlias_Returns404() {
            var response = restTemplate.getForEntity("/does-not-exist", String.class);

            assertThat(response.getStatusCode())
                    .isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("Pagination behaviour tests")
    class PaginationBehaviourTests {
        @Test
        void getAll_ReturnsPaginatedResponse() {
            jdbcTemplate.execute("DELETE FROM shortened_urls");

            for (int i = 1; i <= 15; i++) {
                jdbcTemplate.update(
                        """
                                INSERT INTO shortened_urls (alias, full_url, created_at)
                                VALUES (?, ?, ?)
                                """,
                        "alias" + i,
                        "https://example.com/" + i,
                        Instant.now().plusSeconds(i).toString()
                );
            }

            var response = restTemplate.getForEntity(
                    "/urls?page=0&size=10",
                    PaginatedResponse.class
            );
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            var body = response.getBody();

            assertThat(body.page()).isEqualTo(0);
            assertThat(body.size()).isEqualTo(10);
            assertThat(body.totalElements()).isEqualTo(15);
            assertThat(body.totalPages()).isEqualTo(2);
            assertThat(body.content()).hasSize(10);
        }

        @Test
        void getAll_DefaultsToFirstPage() {
            jdbcTemplate.execute("DELETE FROM shortened_urls");

            for (int i = 1; i <= 12; i++) {
                insertUrl(
                        "alias" + i,
                        "https://example.com/" + i,
                        String.format("2026-01-%02dT10:00:00Z", i)
                );
            }

            var response = restTemplate.exchange(
                    "/urls",
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<PaginatedResponse<UrlListItem>>() {
                    }
            );

            var body = response.getBody();

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(body).isNotNull();
            assertThat(body.page()).isEqualTo(0);
            assertThat(body.size()).isEqualTo(10);
            assertThat(body.totalElements()).isEqualTo(12);
            assertThat(body.content()).hasSize(10);
        }

        @Test
        void getAll_ReturnsCorrectPage() {
            jdbcTemplate.execute("DELETE FROM shortened_urls");

            for (int i = 1; i <= 12; i++) {
                insertUrl("alias" + i,
                        "https://example.com/" + i,
                        String.format("2026-01-%02dT10:00:00Z", i)
                );
            }

            var response = restTemplate.exchange(
                    "/urls?page=1&size=5",
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<PaginatedResponse<UrlListItem>>() {
                    }
            );

            var body = response.getBody();

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(body).isNotNull();
            assertThat(body.content()).extracting(UrlListItem::getAlias).containsExactly(
                    "alias7",
                    "alias6",
                    "alias5",
                    "alias4",
                    "alias3"
            );
        }

        @Test
        void getAll_NegativePage_ReturnsBadRequest() {
            var response = restTemplate.getForEntity(
                    "/urls?page=-1&size=10",
                    String.class
            );

            assertThat(response.getStatusCode())
                    .isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        void getAll_ExcessivePageSize_ReturnsBadRequest() {
            var response = restTemplate.getForEntity(
                    "/urls?page=0&size=101",
                    String.class
            );

            assertThat(response.getStatusCode())
                    .isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @Nested
    @DisplayName("Shorten URL")
    class ShortenUrlTests {
        @Test
        void shorten_ValidUrl_CreatesAndReturnsShortenedUrl() {
            var request = Map.of(
                    "fullUrl", "https://www.example.com/some-page",
                    "customAlias", "myalias"
            );

            var response = restTemplate.postForEntity(
                    "/shorten",
                    request,
                    ShortenUrlResponse.class
            );

            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();

            var body = response.getBody();

            assertThat(body).isNotNull();
            assertThat(body.getAlias()).isEqualTo("myalias");
            assertThat(body.getFullUrl()).isEqualTo("https://www.example.com/some-page");
            assertThat(body.getShortUrl()).endsWith("/myalias");

            var savedUrl = jdbcTemplate.queryForObject(
                    "SELECT full_url FROM shortened_urls WHERE alias = ?",
                    String.class,
                    "myalias"
            );

            assertThat(savedUrl).isEqualTo("https://www.example.com/some-page");
            assertThat(response.getStatusCode()).isEqualTo(OK);
        }
    }

    @Nested
    @DisplayName("DELETE method behaviour")
    class DeleteMethodBehaviour {
        @Test
        void delete_ExistingAlias_DeletesUrlSuccessfully() {
            var response = restTemplate.exchange(
                    "/redir",
                    HttpMethod.DELETE,
                    null,
                    Void.class
            );

            assertThat(response.getStatusCode())
                    .isEqualTo(HttpStatus.NO_CONTENT);

            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM shortened_urls WHERE alias = ?",
                    Integer.class,
                    "redir"
            );

            assertThat(count).isZero();
        }

        @Test
        void delete_UnknownAlias_Returns404() {
            var response = restTemplate.exchange(
                    "/unknown-alias",
                    HttpMethod.DELETE,
                    null,
                    Void.class
            );

            assertThat(response.getStatusCode())
                    .isEqualTo(HttpStatus.NOT_FOUND);
        }
    }
}
