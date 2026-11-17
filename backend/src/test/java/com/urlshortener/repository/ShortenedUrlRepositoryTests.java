package com.urlshortener.repository;

import com.urlshortener.model.UrlListItem;
import org.junit.jupiter.api.*;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ShortenedUrlRepositoryTests {
    
        private static final String BASE_URL = "https://short.example";

        private SingleConnectionDataSource dataSource;
        private JdbcTemplate jdbc;
        private ShortenedUrlRepository repository;

        @BeforeEach
        void setUp() {
            dataSource = new SingleConnectionDataSource("jdbc:sqlite::memory:", true);
            dataSource.setSuppressClose(true);
            jdbc = new JdbcTemplate(dataSource);
            repository = new ShortenedUrlRepository(jdbc);
            repository.init();
        }

        @AfterEach
        void tearDown() {
            dataSource.destroy();
        }

        @Nested
        @DisplayName("Init tests")
        class InitTests {
            @Test
            void createsTable() {
                Integer count = jdbc.queryForObject(
                        "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = 'shortened_urls'",
                        Integer.class);

                assertThat(count).isEqualTo(1);
            }

            @Test
            void isIdempotentAndPreservesExistingData() {
                repository.save("abc", "https://example.com", Instant.parse("2024-01-01T00:00:00Z"));
                repository.init();

                assertThat(repository.existsByAlias("abc")).isTrue();
            }
        }

        @Nested
        @DisplayName("Save tests")
        class SaveTests {
            @Test
            void persistsAllFields() {
                Instant createdAt = Instant.parse("2024-05-01T12:30:00Z");

                repository.save("abc", "https://example.com/page", createdAt);

                var row = jdbc.queryForMap("SELECT alias, full_url, created_at FROM shortened_urls");
                assertThat(row)
                        .containsEntry("alias", "abc")
                        .containsEntry("full_url", "https://example.com/page")
                        .containsEntry("created_at", createdAt.toString());
            }

            @Test
            void rejectsDuplicategetAlias() {
                repository.save("abc", "https://one.example", Instant.now());

                assertThatThrownBy(() -> repository.save("abc", "https://two.example", Instant.now()))
                        .isInstanceOf(DataAccessException.class);

                assertThat(repository.countAll()).isEqualTo(1);
                assertThat(repository.findFullUrlByAlias("abc")).contains("https://one.example");
            }

            @Test
            void allowsSameUrlUnderDifferentAliases() {
                repository.save("a", "https://example.com", Instant.now());
                repository.save("b", "https://example.com", Instant.now());

                assertThat(repository.countAll()).isEqualTo(2);
            }
        }

        @Nested
        @DisplayName("Exists by alias tests")
        class ExistsByAliasTests {

            @Test
            void returnsFalseWhenEmpty() {
                assertThat(repository.existsByAlias("missing")).isFalse();
            }

            @Test
            void returnsTrueWhenPresent() {
                repository.save("abc", "https://example.com", Instant.now());

                assertThat(repository.existsByAlias("abc")).isTrue();
            }

            @Test
            void isCaseSensitive() {
                repository.save("abc", "https://example.com", Instant.now());

                assertThat(repository.existsByAlias("ABC")).isFalse();
            }

            @Test
            void treatsInputAsDataNotSql() {
                repository.save("abc", "https://example.com", Instant.now());

                assertThat(repository.existsByAlias("' OR '1'='1")).isFalse();
            }
        }

        @Nested
        @DisplayName("Find full URL by alias")
        class FindFullUrlBygetAliasTests {

            @Test
            void returnsUrlWhenPresent() {
                repository.save("abc", "https://example.com/page?q=1", Instant.now());

                assertThat(repository.findFullUrlByAlias("abc")).contains("https://example.com/page?q=1");
            }

            @Test
            void returnsEmptyWhenMissing() {
                assertThat(repository.findFullUrlByAlias("missing")).isEmpty();
            }

            @Test
            void returnsCorrectUrlAmongMany() {
                repository.save("a", "https://a.example", Instant.now());
                repository.save("b", "https://b.example", Instant.now());
                repository.save("c", "https://c.example", Instant.now());

                assertThat(repository.findFullUrlByAlias("b")).contains("https://b.example");
            }
        }

        @Nested
        @DisplayName("Find all tests")
        class FindAllTests {

            @Test
            void returnsEmptyListWhenNoRows() {
                assertThat(repository.findAll(BASE_URL, 0, 10)).isEmpty();
            }

            @Test
            void mapsRowsAndBuildsShortUrl() {
                repository.save("abc", "https://example.com", Instant.parse("2024-01-01T00:00:00Z"));
                List<UrlListItem> items = repository.findAll(BASE_URL, 0, 10);

                assertThat(items).containsExactly(
                        new UrlListItem("abc", "https://example.com", BASE_URL + "/abc"));
            }

            @Test
            void ordersByCreatedAtDescending() {
                repository.save("oldest", "https://1.example", Instant.parse("2024-01-01T00:00:00Z"));
                repository.save("newest", "https://3.example", Instant.parse("2024-03-01T00:00:00Z"));
                repository.save("middle", "https://2.example", Instant.parse("2024-02-01T00:00:00Z"));

                assertThat(repository.findAll(BASE_URL, 0, 10))
                        .extracting(UrlListItem::alias)
                        .containsExactly("newest", "middle", "oldest");
            }

            @Test
            void breaksCreatedAtTiesByIdDescending() {
                Instant same = Instant.parse("2024-01-01T00:00:00Z");
                repository.save("first", "https://1.example", same);
                repository.save("second", "https://2.example", same);
                repository.save("third", "https://3.example", same);

                assertThat(repository.findAll(BASE_URL, 0, 10))
                        .extracting(UrlListItem::alias)
                        .containsExactly("third", "second", "first");
            }

            @Test
            void paginatesCorrectly() {
                for (int i = 1; i <= 5; i++) {
                    repository.save("getAlias" + i, "https://" + i + ".example",
                            Instant.parse("2024-01-0" + i + "T00:00:00Z"));
                }

                assertThat(repository.findAll(BASE_URL, 0, 2))
                        .extracting(UrlListItem::alias).containsExactly("getAlias5", "getAlias4");
                assertThat(repository.findAll(BASE_URL, 1, 2))
                        .extracting(UrlListItem::alias).containsExactly("getAlias3", "getAlias2");
                assertThat(repository.findAll(BASE_URL, 2, 2))
                        .extracting(UrlListItem::alias).containsExactly("getAlias1");
            }

            @Test
            void returnsEmptyListForPageBeyondData() {
                repository.save("abc", "https://example.com", Instant.now());

                assertThat(repository.findAll(BASE_URL, 5, 10)).isEmpty();
            }

            @Test
            void pageSizeLargerThanDataReturnsEverything() {
                repository.save("a", "https://a.example", Instant.parse("2024-01-01T00:00:00Z"));
                repository.save("b", "https://b.example", Instant.parse("2024-01-02T00:00:00Z"));

                assertThat(repository.findAll(BASE_URL, 0, 100)).hasSize(2);
            }
        }

        @Nested
        @DisplayName("Count all results test")
        class CountAllTests {
            @Test
            void returnsZeroWhenEmpty() {
                assertThat(repository.countAll()).isZero();
            }

            @Test
            void countsAllRows() {
                repository.save("a", "https://a.example", Instant.now());
                repository.save("b", "https://b.example", Instant.now());
                repository.save("c", "https://c.example", Instant.now());

                assertThat(repository.countAll()).isEqualTo(3);
            }

            @Test
            void reflectsDeletes() {
                repository.save("a", "https://a.example", Instant.now());
                repository.save("b", "https://b.example", Instant.now());

                repository.deleteByAlias("a");

                assertThat(repository.countAll()).isEqualTo(1);
            }
        }

        @Nested
        @DisplayName("Delete entry by alias")
        class DeleteBygetAliasTests {
            @Test
            void returnsTrueAndRemovesRowWhenPresent() {
                repository.save("abc", "https://example.com", Instant.now());

                boolean deleted = repository.deleteByAlias("abc");

                assertThat(deleted).isTrue();
                assertThat(repository.existsByAlias("abc")).isFalse();
                assertThat(repository.findFullUrlByAlias("abc")).isEmpty();
            }

            @Test
            void returnsFalseWhenMissing() {
                assertThat(repository.deleteByAlias("missing")).isFalse();
            }

            @Test
            void onlyDeletesMatchinggetAlias() {
                repository.save("keep", "https://keep.example", Instant.now());
                repository.save("remove", "https://remove.example", Instant.now());

                repository.deleteByAlias("remove");

                assertThat(repository.existsByAlias("keep")).isTrue();
                assertThat(repository.countAll()).isEqualTo(1);
            }

            @Test
            void getAliasCanBeReusedAfterDeletion() {
                repository.save("abc", "https://old.example", Instant.now());
                repository.deleteByAlias("abc");

                repository.save("abc", "https://new.example", Instant.now());

                assertThat(repository.findFullUrlByAlias("abc")).contains("https://new.example");
            }
        }
    }
