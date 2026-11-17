package com.urlshortener.service;

import com.urlshortener.model.PaginatedResponse;
import com.urlshortener.model.ShortenUrlRequest;
import com.urlshortener.model.ShortenUrlResponse;
import com.urlshortener.model.UrlListItem;
import com.urlshortener.repository.ShortenedUrlRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

public class UrlShortenerServiceTests {

    private static final String BASE_URL = "http://localhost:8080";
    private UrlShortenerService service;

    @BeforeEach
    void setUp() {
        var dataSource = new SingleConnectionDataSource("jdbc:sqlite::memory:", true);
        var jdbcTemplate = new JdbcTemplate(dataSource);
        var repository = new ShortenedUrlRepository(jdbcTemplate);
        repository.init();
        service = new UrlShortenerService(repository);
    }

    private ShortenUrlResponse shorten(String fullUrl, String customAlias) {
        var request = new ShortenUrlRequest();
        request.setFullUrl(fullUrl);
        request.setCustomAlias(customAlias);
        return service.shorten(request, BASE_URL);
    }

    private ShortenUrlResponse shorten(String fullUrl) {
        return shorten(fullUrl, null);
    }

    private void seed(int count) {
        for (int i = 1; i <= count; i++) {
            shorten("https://example.com/" + i, "alias-" + i);
        }
    }

    @Nested
    @DisplayName("Shorten - URL validation and normalisation tests")
    class UrlValidationAndNormalisationTests {

        @Test
        void shorten_WithValidUrl_ReturnsShortUrlResponse() {
            var request = new ShortenUrlRequest();
            request.setFullUrl("https://example.com/test");

            ShortenUrlResponse response = service.shorten(request, BASE_URL);

            assertNotNull(response.getAlias());
            assertEquals("https://example.com/test", response.getFullUrl());
            assertTrue(response.getShortUrl().startsWith(BASE_URL + "/"));
        }

        @Test
        void shorten_WithCustomAlias_ReturnsCustomAlias() {
            var request = new ShortenUrlRequest();
            request.setFullUrl("https://example.com");
            request.setCustomAlias("my-alias");

            ShortenUrlResponse response = service.shorten(request, BASE_URL);

            assertEquals("my-alias", response.getAlias());
            assertEquals(BASE_URL + "/my-alias", response.getShortUrl());
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"", "   "})
        void shorten_WithMissingUrl_ThrowsIllegalArgumentException(String fullUrl) {
            var ex = assertThrows(IllegalArgumentException.class, () -> shorten(fullUrl, "valid-alias"));

            assertEquals("fullUrl is required.", ex.getMessage());
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "example.com",            // no scheme
                "not a url",
                "ftp://example.com",      // unsupported scheme
                "mailto:someone@example.com", // scheme but no host
                "http://",                // no authority
                "https://exa mple.com"    // illegal character
        })
        void shorten_WithInvalidUrl_ThrowsIllegalArgumentException(String fullUrl) {
            var ex = assertThrows(IllegalArgumentException.class, () -> shorten(fullUrl, "valid-alias"));

            assertEquals("fullUrl must be a valid URL.", ex.getMessage());
        }

        @Test
        void shorten_WithSurroundingWhitespace_TrimsUrl() {
            var response = shorten("  https://example.com/page  ", "trimmed");

            assertEquals("https://example.com/page", response.getFullUrl());
        }

        @Test
        void shorten_WithNoPath_AddsRootPath() {
            var response = shorten("https://example.com", "root");

            assertEquals("https://example.com/", response.getFullUrl());
        }

        @Test
        void shorten_WithNoPathButQueryAndFragment_AddsRootPathAndKeepsRest() {
            var response = shorten("https://example.com?x=1#frag", "qf");

            assertEquals("https://example.com/?x=1#frag", response.getFullUrl());
        }

        @Test
        void shorten_PreservesPathQueryAndFragment() {
            var response = shorten("https://example.com/a/b?x=1&y=2#top", "full");

            assertEquals("https://example.com/a/b?x=1&y=2#top", response.getFullUrl());
        }

        @Test
        void shorten_PreservesPortAndUserInfo() {
            var response = shorten("http://user:pw@localhost:9090/path", "port");

            assertEquals("http://user:pw@localhost:9090/path", response.getFullUrl());
        }

        @Test
        void shorten_PreservesPercentEncodedSpaceInPath() {
            var response = shorten("https://example.com/a%20b", "space");

            assertEquals("https://example.com/a%20b", response.getFullUrl());
        }

        @Test
        void shorten_WithUppercaseScheme_IsAccepted() {
            assertDoesNotThrow(() -> shorten("HTTPS://example.com/", "upper"));
        }

        @Test
        void shorten_PreservesEncodedQueryDelimiters() {
            var response = shorten("https://example.com/?q=a%26b%3Dc", "enc-query");

            assertEquals("https://example.com/?q=a%26b%3Dc", response.getFullUrl());
        }

        @Test
        void shorten_PreservesEncodedSlashInPath() {
            var response = shorten("https://example.com/a%2Fb", "enc-path");

            assertEquals("https://example.com/a%2Fb", response.getFullUrl());
        }
    }

    @Nested
    @DisplayName("Shorten - alias generation and validation tests")
    class AliasGenerationAndValidationTests {
        @Test
        void shorten_WithNoAlias_GeneratesSevenCharAlphanumericAlias() {
            var response = shorten("https://example.com");

            assertTrue(response.getAlias().matches("[A-Za-z0-9]{7}"),
                    "Unexpected generated alias: " + response.getAlias());
        }

        @Test
        void shorten_WithBlankAlias_GeneratesAlias() {
            var response = shorten("https://example.com", "   ");

            assertTrue(response.getAlias().matches("[A-Za-z0-9]{7}"));
        }

        @Test
        void shorten_SameUrlTwiceWithoutAlias_GeneratesDifferentAliases() {
            var first = shorten("https://example.com");
            var second = shorten("https://example.com");

            assertNotEquals(first.getAlias(), second.getAlias());
        }

        @Test
        void shorten_WithGeneratedAlias_IsRetrievableByThatAlias() {
            var response = shorten("https://example.com/page");

            assertEquals("https://example.com/page", service.getFullUrl(response.getAlias()));
        }

        @Test
        void shorten_WithCustomAliasWhitespace_TrimsAlias() {
            var response = shorten("https://example.com", "  my-alias  ");

            assertEquals("my-alias", response.getAlias());
            assertEquals(BASE_URL + "/my-alias", response.getShortUrl());
            assertNotNull(service.getFullUrl("my-alias"));
        }

        @ParameterizedTest
        @ValueSource(strings = {"ab", "42", "a-b-c", "-leading", "MiXeD-Case-123"})
        void shorten_WithValidCustomAlias_Succeeds(String alias) {
            var response = shorten("https://example.com", alias);

            assertEquals(alias, response.getAlias());
        }

        @Test
        void shorten_WithAliasAtMaxLength_Succeeds() {
            var alias = "a".repeat(64);

            var response = shorten("https://example.com", alias);

            assertEquals(alias, response.getAlias());
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "a",
                " a ",
                "bad alias",
                "under_score",
                "a/b",
                "a.b",
                "a?b",
                "a#b",
                "ab!"
        })
        void shorten_WithInvalidCustomAlias_ThrowsIllegalArgumentException(String alias) {
            var ex = assertThrows(IllegalArgumentException.class, () -> shorten("https://example.com", alias));

            assertTrue(ex.getMessage().startsWith("Alias may only contain letters, numbers, and hyphens"));
        }

        @Test
        void shorten_WithAliasOverMaxLength_ThrowsIllegalArgumentException() {
            var alias = "a".repeat(65);

            assertThrows(IllegalArgumentException.class, () -> shorten("https://example.com", alias));
        }

        @Test
        void shorten_WithTakenAlias_ThrowsIllegalStateException() {
            shorten("https://first.example.com", "taken");

            var ex = assertThrows(IllegalStateException.class,
                    () -> shorten("https://second.example.com", "taken"));

            assertEquals("The alias 'taken' is already taken.", ex.getMessage());
        }

        @Test
        void shorten_WithTakenAlias_DoesNotOverwriteOriginal() {
            shorten("https://first.example.com", "taken");

            assertThrows(IllegalStateException.class, () -> shorten("https://second.example.com", "taken"));

            assertEquals("https://first.example.com/", service.getFullUrl("taken"));
            assertEquals(1, service.getAll(BASE_URL, 0, 10).totalElements());
        }

        @Test
        void shorten_WithTakenAliasInDifferentCase_Succeeds() {
            shorten("https://one.example.com", "Alias");

            assertDoesNotThrow(() -> shorten("https://two.example.com", "alias"));
        }

        @Test
        void shorten_WithInvalidUrlAndTakenAlias_ReportsInvalidUrlFirst() {
            shorten("https://example.com", "taken");

            assertThrows(IllegalArgumentException.class, () -> shorten("not a url", "taken"));
        }

        @Test
        void shorten_WithInvalidUrl_DoesNotSaveAnything() {
            assertThrows(IllegalArgumentException.class, () -> shorten("not a url", "valid-alias"));

            assertNull(service.getFullUrl("valid-alias"));
            assertEquals(0, service.getAll(BASE_URL, 0, 10).totalElements());
        }

        @Test
        void shorten_WithInvalidAlias_DoesNotSaveAnything() {
            assertThrows(IllegalArgumentException.class, () -> shorten("https://example.com", "bad alias"));

            assertEquals(0, service.getAll(BASE_URL, 0, 10).totalElements());
        }

        @Test
        void shorten_BuildsShortUrlFromBaseUrlAndAlias() {
            var response = shorten("https://example.com/page", "short");

            assertEquals("short", response.getAlias());
            assertEquals(BASE_URL + "/short", response.getShortUrl());
            assertEquals("https://example.com/page", response.getFullUrl());
        }
    }

    @Nested
    @DisplayName("Get full URL tests")
    class GetFullURLTests {
        @Test
        void getFullUrl_ReturnsExistingUrl() throws ExecutionException, InterruptedException {
            var request = new ShortenUrlRequest();
            request.setFullUrl("https://example.com");
            request.setCustomAlias("example");
            service.shorten(request, BASE_URL);

            String fullUrl = service.getFullUrl("example");

            assertEquals("https://example.com/", fullUrl);
        }

        @Test
        void getFullUrl_WithUnknownAlias_ReturnsNull() {
            assertNull(service.getFullUrl("missing"));
        }

        @Test
        void getFullUrl_ReturnsNormalizedUrl() {
            shorten("  https://example.com  ", "norm");

            assertEquals("https://example.com/", service.getFullUrl("norm"));
        }

        @Test
        void getFullUrl_AfterDelete_ReturnsNull() {
            shorten("https://example.com", "gone");
            service.delete("gone");

            assertNull(service.getFullUrl("gone"));
        }
    }

    @Nested
    @DisplayName("Get all tests")
    class GetAllTests {
        @Test
        void getAll_WhenEmpty_ReturnsEmptyPage() {
            PaginatedResponse<UrlListItem> result = service.getAll(BASE_URL, 0, 10);

            assertTrue(result.content().isEmpty());
            assertEquals(0, result.totalElements());
            assertEquals(0, result.totalPages());
            assertEquals(0, result.page());
            assertEquals(10, result.size());
        }

        @Test
        void getAll_MapsItemsAndBuildsShortUrls() {
            shorten("https://example.com/page", "mapped");

            PaginatedResponse<UrlListItem> result = service.getAll(BASE_URL, 0, 10);

            assertEquals(1, result.content().size());
            var item = result.content().get(0);
            assertEquals("mapped", item.alias());
            assertEquals("https://example.com/page", item.fullUrl());
            assertEquals(BASE_URL + "/mapped", item.shortUrl());
        }

        @Test
        void getAll_ReturnsNewestFirst() {
            seed(3);

            var aliases = service.getAll(BASE_URL, 0, 10).content().stream()
                    .map(UrlListItem::alias)
                    .toList();

            assertEquals(java.util.List.of("alias-3", "alias-2", "alias-1"), aliases);
        }

        @Test
        void getAll_ReturnsRequestedPageAndMetadata() {
            seed(25);

            PaginatedResponse<UrlListItem> result = service.getAll(BASE_URL, 1, 10);

            assertEquals(10, result.content().size());
            assertEquals(1, result.page());
            assertEquals(10, result.size());
            assertEquals(25, result.totalElements());
            assertEquals(3, result.totalPages());
        }

        @Test
        void getAll_LastPartialPage_ReturnsRemainingItems() {
            seed(25);

            PaginatedResponse<UrlListItem> result = service.getAll(BASE_URL, 2, 10);

            assertEquals(5, result.content().size());
            assertEquals("alias-5", result.content().get(0).alias());
            assertEquals("alias-1", result.content().get(4).alias());
        }

        @Test
        void getAll_WhenCountIsExactMultipleOfSize_DoesNotAddExtraPage() {
            seed(20);

            assertEquals(2, service.getAll(BASE_URL, 0, 10).totalPages());
        }

        @Test
        void getAll_WithPageSizeLargerThanTotal_ReturnsSinglePage() {
            seed(3);

            PaginatedResponse<UrlListItem> result = service.getAll(BASE_URL, 0, 100);

            assertEquals(3, result.content().size());
            assertEquals(1, result.totalPages());
        }

        @Test
        void getAll_WithPageBeyondData_ReturnsEmptyContentButKeepsTotals() {
            seed(5);

            PaginatedResponse<UrlListItem> result = service.getAll(BASE_URL, 9, 10);

            assertTrue(result.content().isEmpty());
            assertEquals(5, result.totalElements());
            assertEquals(1, result.totalPages());
            assertEquals(9, result.page());
        }

        @Test
        void getAll_UsesSuppliedBaseUrl() {
            shorten("https://example.com", "based");

            var item = service.getAll("https://sho.rt", 0, 10).content().get(0);

            assertEquals("https://sho.rt/based", item.shortUrl());
        }
    }

    @Nested
    @DisplayName("Delete tests")
    class DeleteTests {
        @Test
        void delete_WithExistingAlias_ReturnsTrueAndRemovesIt() {
            shorten("https://example.com", "remove-me");

            assertTrue(service.delete("remove-me"));

            assertNull(service.getFullUrl("remove-me"));
            assertEquals(0, service.getAll(BASE_URL, 0, 10).totalElements());
        }

        @Test
        void delete_WithUnknownAlias_ReturnsFalse() {
            assertFalse(service.delete("missing"));
        }

        @Test
        void delete_CalledTwice_ReturnsFalseTheSecondTime() {
            shorten("https://example.com", "once");

            assertTrue(service.delete("once"));
            assertFalse(service.delete("once"));
        }

        @Test
        void delete_OnlyRemovesTheGivenAlias() {
            shorten("https://keep.example.com", "keep");
            shorten("https://drop.example.com", "drop");

            service.delete("drop");

            assertEquals("https://keep.example.com/", service.getFullUrl("keep"));
            assertEquals(1, service.getAll(BASE_URL, 0, 10).totalElements());
        }

        @Test
        void delete_FreesTheAliasForReuse() {
            shorten("https://old.example.com", "reuse");
            service.delete("reuse");

            var response = shorten("https://new.example.com", "reuse");

            assertEquals("reuse", response.getAlias());
            assertEquals("https://new.example.com/", service.getFullUrl("reuse"));
        }
    }
}
