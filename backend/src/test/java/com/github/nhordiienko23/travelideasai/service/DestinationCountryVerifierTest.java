package com.github.nhordiienko23.travelideasai.service;

import com.github.nhordiienko23.travelideasai.exception.InvalidAiResponseException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
@DisplayName("Destination Country Verifier Tests")
class DestinationCountryVerifierTest {

    private static final String USER_AGENT =
            "TravelIdeasAI-Test/1.0";

    private StubGeocodingServer server;

    private DestinationCountryVerifier verifier;

    @BeforeEach
    void setUp() throws IOException {
        server = new StubGeocodingServer();

        verifier = createVerifier(server.baseUrl());
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    private DestinationCountryVerifier createVerifier(String baseUrl) {
        return new DestinationCountryVerifier(
                JsonMapper.builder().build(),
                baseUrl,
                USER_AGENT,
                Duration.ofSeconds(2),
                Duration.ofSeconds(2)
        );
    }

    // --------------------------------------------------
    // Successful verification
    // --------------------------------------------------

    @Test
    @DisplayName("Should verify a city and return its canonical English name")
    void verifiesCityAndReturnsCanonicalEnglishName() {
        server.respond(
                200,
                listJson(
                        placeJson(
                                "city",
                                "city",
                                "Odesa",
                                "Odesa, Odeska Oblast, Ukraine",
                                "Ukraine",
                                """
                                {
                                  "name:en": "Odesa",
                                  "int_name": "Odesa",
                                  "alt_name": "Odessa"
                                }
                                """
                        )
                )
        );

        assertThat(
                verifier.verifyAndGetCanonicalDestination(
                        "Odessa",
                        "Ukraine"
                )
        ).isEqualTo("Odesa");

        assertThat(server.requestCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should support the legacy verify method")
    void supportsLegacyVerifyMethod() {
        server.respond(
                200,
                listJson(validPlace("Paris", "France"))
        );

        assertDoesNotThrow(
                () -> verifier.verify("Paris", "France")
        );

        assertThat(server.requestCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should trim destination and country input")
    void trimsInputValues() {
        server.respond(
                200,
                listJson(validPlace("Paris", "France"))
        );

        assertThat(
                verifier.verifyAndGetCanonicalDestination(
                        "  Paris  ",
                        "  France  "
                )
        ).isEqualTo("Paris");

        assertThat(server.requestCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should accept a valid destination when non-matching results appear first")
    void skipsNonMatchingResultsAndFindsCorrectDestination() {
        server.respond(
                200,
                listJson(
                        validPlace("London", "United Kingdom"),
                        validPlace("Paris", "France"),
                        validPlace("Berlin", "Germany")
                )
        );

        assertThat(
                verifier.verifyAndGetCanonicalDestination(
                        "Paris",
                        "France"
                )
        ).isEqualTo("Paris");
    }

    @Test
    @DisplayName("Should skip non-object search results")
    void skipsNonObjectSearchResults() {
        server.respond(
                200,
                """
                [
                  null,
                  123,
                  "unexpected result",
                  %s
                ]
                """.formatted(validPlace("Paris", "France"))
        );

        assertThat(
                verifier.verifyAndGetCanonicalDestination(
                        "Paris",
                        "France"
                )
        ).isEqualTo("Paris");
    }

    @Test
    @DisplayName("Should continue searching after a matching result without an address")
    void continuesAfterMatchingResultWithoutAddress() {
        server.respond(
                200,
                listJson(
                        placeJsonWithAddress(
                                "city",
                                "city",
                                "Paris",
                                "Paris, France",
                                "null",
                                "{}"
                        ),
                        validPlace("Paris", "France")
                )
        );

        assertThat(
                verifier.verifyAndGetCanonicalDestination(
                        "Paris",
                        "France"
                )
        ).isEqualTo("Paris");
    }

    @Test
    @DisplayName("Should continue searching after a matching result with an incorrect country")
    void continuesAfterMatchingResultWithIncorrectCountry() {
        server.respond(
                200,
                listJson(
                        validPlace("Paris", "United States"),
                        validPlace("Paris", "France")
                )
        );

        assertThat(
                verifier.verifyAndGetCanonicalDestination(
                        "Paris",
                        "France"
                )
        ).isEqualTo("Paris");
    }

    // --------------------------------------------------
    // Input validation
    // --------------------------------------------------

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidInputs")
    @DisplayName("Should reject null or blank destination and country")
    void rejectsInvalidInputs(
            String scenario,
            String destination,
            String country
    ) {
        InvalidAiResponseException exception = assertThrows(
                InvalidAiResponseException.class,
                () -> verifier.verifyAndGetCanonicalDestination(
                        destination,
                        country
                )
        );

        assertThat(exception)
                .hasMessage(
                        "Destination and country are required for verification."
                );

        assertThat(server.requestCount()).isZero();
    }

    private static Stream<Arguments> invalidInputs() {
        return Stream.of(
                Arguments.of("null destination", null, "Ukraine"),
                Arguments.of("blank destination", " ", "Ukraine"),
                Arguments.of("empty destination", "", "Ukraine"),
                Arguments.of("null country", "Odesa", null),
                Arguments.of("blank country", "Odesa", " "),
                Arguments.of("empty country", "Odesa", "")
        );
    }

    // --------------------------------------------------
    // Settlement types
    // --------------------------------------------------

    @ParameterizedTest(name = "type={0}, addresstype={1}")
    @MethodSource("validSettlementTypes")
    @DisplayName("Should accept city and town settlement types")
    void acceptsCityAndTownTypes(
            String type,
            String addressType
    ) {
        server.respond(
                200,
                listJson(
                        placeJson(
                                type,
                                addressType,
                                "Test City",
                                "Test City, Region, Ukraine",
                                "Ukraine",
                                "{}"
                        )
                )
        );

        assertThat(
                verifier.verifyAndGetCanonicalDestination(
                        "Test City",
                        "Ukraine"
                )
        ).isEqualTo("Test City");
    }

    private static Stream<Arguments> validSettlementTypes() {
        return Stream.of(
                Arguments.of("city", "city"),
                Arguments.of("town", "town"),
                Arguments.of("city", "administrative"),
                Arguments.of("administrative", "city"),
                Arguments.of("administrative", "town"),
                Arguments.of("CITY", "administrative"),
                Arguments.of("administrative", "TOWN")
        );
    }

    @Test
    @DisplayName("Should reject a village even when its name and country match")
    void rejectsVillageSettlement() {
        server.respond(
                200,
                listJson(
                        placeJson(
                                "village",
                                "village",
                                "Small Town",
                                "Small Town, Ukraine",
                                "Ukraine",
                                "{}"
                        )
                )
        );

        assertThatThrownBy(
                () -> verifier.verifyAndGetCanonicalDestination(
                        "Small Town",
                        "Ukraine"
                )
        )
                .isInstanceOf(InvalidAiResponseException.class)
                .hasMessageContaining(
                        "Destination could not be verified as a city or town"
                );
    }

    @Test
    @DisplayName("Should reject a result with neither city nor town type")
    void rejectsUnsupportedSettlementType() {
        server.respond(
                200,
                listJson(
                        placeJson(
                                "administrative",
                                "municipality",
                                "Paris",
                                "Paris, France",
                                "France",
                                "{}"
                        )
                )
        );

        assertThatThrownBy(
                () -> verifier.verifyAndGetCanonicalDestination(
                        "Paris",
                        "France"
                )
        )
                .isInstanceOf(InvalidAiResponseException.class)
                .hasMessageContaining("city or town");
    }

    // --------------------------------------------------
    // Destination name matching
    // --------------------------------------------------

    @ParameterizedTest(name = "Should recognize name field: {0}")
    @MethodSource("recognizedNameDetailFields")
    @DisplayName("Should match destinations using supported Nominatim name fields")
    void matchesSupportedNameDetailFields(String field) {
        String namedetails = """
                {
                  "%s": "Requested Alias"
                }
                """.formatted(field);

        server.respond(
                200,
                listJson(
                        placeJson(
                                "city",
                                "city",
                                "Canonical City",
                                "Canonical City, Region, Ukraine",
                                "Ukraine",
                                namedetails
                        )
                )
        );

        String canonicalName =
                verifier.verifyAndGetCanonicalDestination(
                        "Requested Alias",
                        "Ukraine"
                );

        assertThat(canonicalName).isNotBlank();
    }

    private static Stream<Arguments> recognizedNameDetailFields() {
        return Stream.of(
                "name",
                "name:en",
                "name:uk",
                "name:de",
                "alt_name",
                "alt_name:en",
                "alt_name:uk",
                "old_name",
                "old_name:en",
                "int_name",
                "official_name",
                "official_name:en",
                "short_name",
                "short_name:en",
                "loc_name",
                "loc_name:en"
        ).map(Arguments::of);
    }

    @Test
    @DisplayName("Should match alternative names separated by semicolons")
    void matchesSemicolonSeparatedAlternativeNames() {
        server.respond(
                200,
                listJson(
                        placeJson(
                                "city",
                                "city",
                                "Odesa",
                                "Odesa, Ukraine",
                                "Ukraine",
                                """
                                {
                                  "alt_name": "Old Name; Odessa; Historical Name"
                                }
                                """
                        )
                )
        );

        assertThat(
                verifier.verifyAndGetCanonicalDestination(
                        "Historical Name",
                        "Ukraine"
                )
        ).isEqualTo("Odesa");
    }

    @Test
    @DisplayName("Should match alternative names represented as an array")
    void matchesAlternativeNamesRepresentedAsArray() {
        server.respond(
                200,
                listJson(
                        placeJson(
                                "city",
                                "city",
                                "Odesa",
                                "Odesa, Ukraine",
                                "Ukraine",
                                """
                                {
                                  "alt_name": [
                                    "Old Name",
                                    "Odessa",
                                    "Historical Name"
                                  ]
                                }
                                """
                        )
                )
        );

        assertThat(
                verifier.verifyAndGetCanonicalDestination(
                        "Historical Name",
                        "Ukraine"
                )
        ).isEqualTo("Odesa");
    }

    @Test
    @DisplayName("Should use the first component of display name for matching")
    void matchesFirstDisplayNameComponent() {
        server.respond(
                200,
                listJson(
                        placeJson(
                                "city",
                                "city",
                                "Canonical Name",
                                "Requested City, Region, Ukraine",
                                "Ukraine",
                                "{}"
                        )
                )
        );

        assertThat(
                verifier.verifyAndGetCanonicalDestination(
                        "Requested City",
                        "Ukraine"
                )
        ).isEqualTo("Canonical Name");
    }

    @Test
    @DisplayName("Should ignore unrelated namedetails fields")
    void ignoresUnrelatedNameDetailsFields() {
        server.respond(
                200,
                listJson(
                        placeJson(
                                "city",
                                "city",
                                "Canonical City",
                                "Canonical City, Ukraine",
                                "Ukraine",
                                """
                                {
                                  "brand": "Requested Alias",
                                  "operator": "Requested Alias",
                                  "website": "Requested Alias"
                                }
                                """
                        )
                )
        );

        assertThatThrownBy(
                () -> verifier.verifyAndGetCanonicalDestination(
                        "Requested Alias",
                        "Ukraine"
                )
        )
                .isInstanceOf(InvalidAiResponseException.class)
                .hasMessageContaining("could not be verified");
    }

    @Test
    @DisplayName("Should safely ignore namedetails when it is not a JSON object")
    void ignoresNamedetailsWithWrongType() {
        server.respond(
                200,
                listJson(
                        placeJson(
                                "city",
                                "city",
                                "Paris",
                                "Paris, France",
                                "France",
                                "\"not-an-object\""
                        )
                )
        );

        assertThat(
                verifier.verifyAndGetCanonicalDestination(
                        "Paris",
                        "France"
                )
        ).isEqualTo("Paris");
    }

    // --------------------------------------------------
    // Name normalization
    // --------------------------------------------------

    @ParameterizedTest(name = "Should normalize {0} to {1}")
    @MethodSource("normalizedDestinationNames")
    @DisplayName("Should match destination names regardless of accents and punctuation")
    void matchesNormalizedDestinationNames(
            String requestedName,
            String actualName
    ) {
        server.respond(
                200,
                listJson(
                        validPlace(actualName, "Poland")
                )
        );

        assertThat(
                verifier.verifyAndGetCanonicalDestination(
                        requestedName,
                        "Poland"
                )
        ).isEqualTo(actualName);
    }

    private static Stream<Arguments> normalizedDestinationNames() {
        return Stream.of(
                Arguments.of("Sao Tome", "São Tomé"),
                Arguments.of("Lodz", "Łódź"),
                Arguments.of("New York", "New-York"),
                Arguments.of("Strasse", "Straße"),
                Arguments.of("Aero", "Ærø")
        );
    }

    @Test
    @DisplayName("Should match destination names case-insensitively")
    void matchesDestinationNamesIgnoringCase() {
        server.respond(
                200,
                listJson(validPlace("Odesa", "Ukraine"))
        );

        assertThat(
                verifier.verifyAndGetCanonicalDestination(
                        "ODESA",
                        "Ukraine"
                )
        ).isEqualTo("Odesa");
    }

    @Test
    @DisplayName("Should match destination names with repeated whitespace")
    void matchesDestinationNamesWithDifferentWhitespace() {
        server.respond(
                200,
                listJson(validPlace("New York", "United States"))
        );

        assertThat(
                verifier.verifyAndGetCanonicalDestination(
                        "  New    York  ",
                        "United States"
                )
        ).isEqualTo("New York");
    }

    // --------------------------------------------------
    // Canonical name resolution
    // --------------------------------------------------

    @Test
    @DisplayName("Should prefer the English name over international and local names")
    void prefersEnglishCanonicalName() {
        server.respond(
                200,
                listJson(
                        placeJson(
                                "city",
                                "city",
                                "Одеса",
                                "Одеса, Україна",
                                "Ukraine",
                                """
                                {
                                  "name:en": "Odesa",
                                  "int_name": "Odessa International",
                                  "alt_name": "Odessa"
                                }
                                """
                        )
                )
        );

        assertThat(
                verifier.verifyAndGetCanonicalDestination(
                        "Odessa",
                        "Ukraine"
                )
        ).isEqualTo("Odesa");
    }

    @Test
    @DisplayName("Should use international name when English name is unavailable")
    void usesInternationalNameAsFallback() {
        server.respond(
                200,
                listJson(
                        placeJson(
                                "city",
                                "city",
                                "Київ",
                                "Київ, Україна",
                                "Ukraine",
                                """
                                {
                                  "int_name": "Kyiv",
                                  "alt_name": "Kiev"
                                }
                                """
                        )
                )
        );

        assertThat(
                verifier.verifyAndGetCanonicalDestination(
                        "Kiev",
                        "Ukraine"
                )
        ).isEqualTo("Kyiv");
    }

    @Test
    @DisplayName("Should use the primary place name when English and international names are unavailable")
    void usesPrimaryPlaceNameAsFallback() {
        server.respond(
                200,
                listJson(
                        placeJson(
                                "city",
                                "city",
                                "Odesa",
                                "Odesa, Ukraine",
                                "Ukraine",
                                """
                                {
                                  "alt_name": "Odessa"
                                }
                                """
                        )
                )
        );

        assertThat(
                verifier.verifyAndGetCanonicalDestination(
                        "Odessa",
                        "Ukraine"
                )
        ).isEqualTo("Odesa");
    }

    @Test
    @DisplayName("Should use the first display name component when primary name is unavailable")
    void usesDisplayNameAsFallback() {
        server.respond(
                200,
                listJson(
                        placeJson(
                                "city",
                                "city",
                                null,
                                "Display City, Region, Ukraine",
                                "Ukraine",
                                """
                                {
                                  "alt_name": "Requested Alias"
                                }
                                """
                        )
                )
        );

        assertThat(
                verifier.verifyAndGetCanonicalDestination(
                        "Requested Alias",
                        "Ukraine"
                )
        ).isEqualTo("Display City");
    }

    @Test
    @DisplayName("Should use requested destination as the final canonical name fallback")
    void usesRequestedDestinationAsFinalFallback() {
        server.respond(
                200,
                listJson(
                        placeJson(
                                "city",
                                "city",
                                null,
                                null,
                                "Ukraine",
                                """
                                {
                                  "alt_name": "Requested City"
                                }
                                """
                        )
                )
        );

        assertThat(
                verifier.verifyAndGetCanonicalDestination(
                        "Requested City",
                        "Ukraine"
                )
        ).isEqualTo("Requested City");
    }

    @Test
    @DisplayName("Should use the first English name when it contains semicolon-separated values")
    void usesFirstSemicolonSeparatedEnglishName() {
        server.respond(
                200,
                listJson(
                        placeJson(
                                "city",
                                "city",
                                "Odesa",
                                "Odesa, Ukraine",
                                "Ukraine",
                                """
                                {
                                  "name:en": "Odesa;Odessa;Other Name",
                                  "alt_name": "Requested Alias"
                                }
                                """
                        )
                )
        );

        assertThat(
                verifier.verifyAndGetCanonicalDestination(
                        "Requested Alias",
                        "Ukraine"
                )
        ).isEqualTo("Odesa");
    }

    @Test
    @DisplayName("Should use the first English name when name details contain a list")
    void usesFirstEnglishNameFromArray() {
        server.respond(
                200,
                listJson(
                        placeJson(
                                "city",
                                "city",
                                "Odesa",
                                "Odesa, Ukraine",
                                "Ukraine",
                                """
                                {
                                  "name:en": ["Odesa", "Odessa"],
                                  "alt_name": "Requested Alias"
                                }
                                """
                        )
                )
        );

        assertThat(
                verifier.verifyAndGetCanonicalDestination(
                        "Requested Alias",
                        "Ukraine"
                )
        ).isEqualTo("Odesa");
    }

    @Test
    @DisplayName("Should fall back to international name when English name has an invalid type")
    void fallsBackWhenEnglishNameHasInvalidType() {
        server.respond(
                200,
                listJson(
                        placeJson(
                                "city",
                                "city",
                                "Київ",
                                "Київ, Україна",
                                "Ukraine",
                                """
                                {
                                  "name:en": 123,
                                  "int_name": "Kyiv",
                                  "alt_name": "Kiev"
                                }
                                """
                        )
                )
        );

        assertThat(
                verifier.verifyAndGetCanonicalDestination(
                        "Kiev",
                        "Ukraine"
                )
        ).isEqualTo("Kyiv");
    }

    // --------------------------------------------------
    // Country verification
    // --------------------------------------------------

    @ParameterizedTest(name = "Actual country={0}, expected country={1}")
    @MethodSource("countryAliases")
    @DisplayName("Should recognize supported country name aliases")
    void recognizesCountryAliases(
            String actualCountry,
            String expectedCountry
    ) {
        server.respond(
                200,
                listJson(validPlace("Test City", actualCountry))
        );

        assertThat(
                verifier.verifyAndGetCanonicalDestination(
                        "Test City",
                        expectedCountry
                )
        ).isEqualTo("Test City");
    }

    private static Stream<Arguments> countryAliases() {
        return Stream.of(
                Arguments.of("Czechia", "Czech Republic"),
                Arguments.of("Moldova", "Republic of Moldova"),
                Arguments.of("Moldova", "Moldova Republic of"),
                Arguments.of("United States", "USA"),
                Arguments.of("United States of America", "United States"),
                Arguments.of("United Kingdom", "UK"),
                Arguments.of("Turkey", "Türkiye"),
                Arguments.of("Côte d'Ivoire", "Cote d Ivoire")
        );
    }

    @Test
    @DisplayName("Should reject a destination returned in the wrong country")
    void rejectsIncorrectCountry() {
        server.respond(
                200,
                listJson(
                        validPlace("Paris", "United States")
                )
        );

        assertThatThrownBy(
                () -> verifier.verifyAndGetCanonicalDestination(
                        "Paris",
                        "France"
                )
        )
                .isInstanceOf(InvalidAiResponseException.class)
                .hasMessageContaining(
                        "AI returned an incorrect country"
                );
    }

    @Test
    @DisplayName("Should distinguish an unknown destination from an incorrect country")
    void distinguishesUnknownDestinationFromWrongCountry() {
        server.respond(
                200,
                listJson(
                        validPlace("London", "United Kingdom")
                )
        );

        assertThatThrownBy(
                () -> verifier.verifyAndGetCanonicalDestination(
                        "Paris",
                        "France"
                )
        )
                .isInstanceOf(InvalidAiResponseException.class)
                .hasMessageContaining(
                        "Destination could not be verified as a city or town"
                );
    }

    @Test
    @DisplayName("Should reject a result without a country in its address")
    void rejectsResultWithoutCountry() {
        server.respond(
                200,
                listJson(
                        placeJson(
                                "city",
                                "city",
                                "Paris",
                                "Paris, France",
                                null,
                                "{}"
                        )
                )
        );

        assertThatThrownBy(
                () -> verifier.verifyAndGetCanonicalDestination(
                        "Paris",
                        "France"
                )
        )
                .isInstanceOf(InvalidAiResponseException.class)
                .hasMessageContaining(
                        "Could not confirm the country"
                );
    }

    @Test
    @DisplayName("Should reject a blank country in the address")
    void rejectsBlankCountryInAddress() {
        server.respond(
                200,
                listJson(
                        placeJson(
                                "city",
                                "city",
                                "Paris",
                                "Paris, France",
                                " ",
                                "{}"
                        )
                )
        );

        assertThatThrownBy(
                () -> verifier.verifyAndGetCanonicalDestination(
                        "Paris",
                        "France"
                )
        )
                .isInstanceOf(InvalidAiResponseException.class)
                .hasMessageContaining(
                        "Could not confirm the country"
                );
    }

    @Test
    @DisplayName("Should reject a non-string country in the address")
    void rejectsNonStringCountryInAddress() {
        server.respond(
                200,
                """
                [
                  {
                    "type": "city",
                    "addresstype": "city",
                    "name": "Paris",
                    "display_name": "Paris, France",
                    "address": {
                      "country": 123
                    }
                  }
                ]
                """
        );

        assertThatThrownBy(
                () -> verifier.verifyAndGetCanonicalDestination(
                        "Paris",
                        "France"
                )
        )
                .isInstanceOf(InvalidAiResponseException.class)
                .hasMessageContaining(
                        "Could not confirm the country"
                );
    }

    @Test
    @DisplayName("Should reject a result without an address object")
    void rejectsMissingAddressObject() {
        server.respond(
                200,
                listJson(
                        placeJsonWithAddress(
                                "city",
                                "city",
                                "Paris",
                                "Paris, France",
                                "null",
                                "{}"
                        )
                )
        );

        assertThatThrownBy(
                () -> verifier.verifyAndGetCanonicalDestination(
                        "Paris",
                        "France"
                )
        )
                .isInstanceOf(InvalidAiResponseException.class)
                .hasMessageContaining(
                        "Could not confirm the country"
                );
    }

    @Test
    @DisplayName("Should reject a result when address is not an object")
    void rejectsAddressWithWrongType() {
        server.respond(
                200,
                listJson(
                        placeJsonWithAddress(
                                "city",
                                "city",
                                "Paris",
                                "Paris, France",
                                "\"France\"",
                                "{}"
                        )
                )
        );

        assertThatThrownBy(
                () -> verifier.verifyAndGetCanonicalDestination(
                        "Paris",
                        "France"
                )
        )
                .isInstanceOf(InvalidAiResponseException.class)
                .hasMessageContaining(
                        "Could not confirm the country"
                );
    }

    // --------------------------------------------------
    // Empty and invalid geographical responses
    // --------------------------------------------------

    @Test
    @DisplayName("Should reject an empty search result list")
    void rejectsEmptySearchResults() {
        server.respond(200, "[]");

        assertThatThrownBy(
                () -> verifier.verifyAndGetCanonicalDestination(
                        "Atlantis",
                        "Unknown Country"
                )
        )
                .isInstanceOf(InvalidAiResponseException.class)
                .hasMessageContaining(
                        "Destination and country could not be verified"
                );
    }

    @Test
    @DisplayName("Should reject null response body")
    void rejectsNullResponseBody() {
        server.respond(204, "");

        assertThatThrownBy(
                () -> verifier.verifyAndGetCanonicalDestination(
                        "Paris",
                        "France"
                )
        )
                .isInstanceOf(InvalidAiResponseException.class)
                .hasMessageContaining(
                        "Geographical service returned an empty response"
                );
    }

    @Test
    @DisplayName("Should reject blank response body")
    void rejectsBlankResponseBody() {
        server.respond(200, "   \n\t ");

        assertThatThrownBy(
                () -> verifier.verifyAndGetCanonicalDestination(
                        "Paris",
                        "France"
                )
        )
                .isInstanceOf(InvalidAiResponseException.class)
                .hasMessageContaining(
                        "Geographical service returned an empty response"
                );
    }

    @Test
    @DisplayName("Should reject malformed JSON returned by geographical service")
    void rejectsMalformedJson() {
        server.respond(
                200,
                """
                [
                  {
                    "type": "city",
                    "name": "Paris",
                    "address": {
                      "country": "France"
                    }
                """
        );

        assertThatThrownBy(
                () -> verifier.verifyAndGetCanonicalDestination(
                        "Paris",
                        "France"
                )
        )
                .isInstanceOf(InvalidAiResponseException.class)
                .hasMessageContaining(
                        "Geographical service returned invalid data"
                );
    }

    @Test
    @DisplayName("Should reject a JSON root that is not an array")
    void rejectsNonArrayJsonRoot() {
        server.respond(
                200,
                """
                {
                  "type": "city",
                  "name": "Paris",
                  "country": "France"
                }
                """
        );

        assertThatThrownBy(
                () -> verifier.verifyAndGetCanonicalDestination(
                        "Paris",
                        "France"
                )
        )
                .isInstanceOf(InvalidAiResponseException.class)
                .hasMessageContaining(
                        "Geographical service returned an invalid response"
                );
    }

    @Test
    @DisplayName("Should reject a JSON null root")
    void rejectsJsonNullRoot() {
        server.respond(200, "null");

        assertThatThrownBy(
                () -> verifier.verifyAndGetCanonicalDestination(
                        "Paris",
                        "France"
                )
        )
                .isInstanceOf(InvalidAiResponseException.class)
                .hasMessageContaining(
                        "invalid response"
                );
    }

    @Test
    @DisplayName("Should reject a JSON scalar root")
    void rejectsJsonScalarRoot() {
        server.respond(200, "123");

        assertThatThrownBy(
                () -> verifier.verifyAndGetCanonicalDestination(
                        "Paris",
                        "France"
                )
        )
                .isInstanceOf(InvalidAiResponseException.class)
                .hasMessageContaining(
                        "invalid response"
                );
    }

    // --------------------------------------------------
    // HTTP request construction
    // --------------------------------------------------

    @Test
    @DisplayName("Should send the required Nominatim search parameters and headers")
    void sendsCorrectNominatimRequest() {
        server.respond(
                200,
                listJson(validPlace("Odesa", "Ukraine"))
        );

        verifier.verifyAndGetCanonicalDestination(
                "Odesa",
                "Ukraine"
        );

        assertThat(server.lastMethod()).isEqualTo("GET");
        assertThat(server.lastUri().getPath()).isEqualTo("/search");

        String query = URLDecoder.decode(
                server.lastUri().getRawQuery(),
                StandardCharsets.UTF_8
        );

        assertThat(query)
                .contains("q=Odesa, Ukraine")
                .contains("featureType=settlement")
                .contains("format=jsonv2")
                .contains("addressdetails=1")
                .contains("namedetails=1")
                .contains("limit=5")
                .contains("accept-language=en");

        assertThat(server.lastUserAgent()).isEqualTo(USER_AGENT);
        assertThat(server.lastAccept()).contains("application/json");
    }

    // --------------------------------------------------
    // HTTP failures
    // --------------------------------------------------

    @Test
    @DisplayName("Should convert HTTP server errors to destination verification errors")
    void handlesHttpServerError() {
        server.respond(
                500,
                """
                {
                  "error": "Internal Server Error"
                }
                """
        );

        InvalidAiResponseException exception = assertThrows(
                InvalidAiResponseException.class,
                () -> verifier.verifyAndGetCanonicalDestination(
                        "Paris",
                        "France"
                )
        );

        assertThat(exception)
                .hasMessageContaining(
                        "Destination verification is temporarily unavailable"
                );

        assertThat(exception.getCause())
                .isInstanceOf(
                        org.springframework.web.client.RestClientException.class
                );

        assertThat(server.requestCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should handle connection failures")
    void handlesConnectionFailure() {
        // Stop the local HTTP server so the configured port refuses connections.
        server.stop();

        InvalidAiResponseException exception = assertThrows(
                InvalidAiResponseException.class,
                () -> verifier.verifyAndGetCanonicalDestination(
                        "Paris",
                        "France"
                )
        );

        assertThat(exception)
                .hasMessageContaining(
                        "Destination verification is temporarily unavailable"
                );

        assertThat(exception.getCause())
                .isInstanceOf(
                        org.springframework.web.client.RestClientException.class
                );
    }

    // --------------------------------------------------
    // Verification cache
    // --------------------------------------------------

    @Test
    @DisplayName("Should cache a successfully verified destination")
    void cachesSuccessfulVerification() {
        server.respond(
                200,
                listJson(validPlace("Paris", "France"))
        );

        assertThat(
                verifier.verifyAndGetCanonicalDestination(
                        "Paris",
                        "France"
                )
        ).isEqualTo("Paris");

        assertThat(
                verifier.verifyAndGetCanonicalDestination(
                        "Paris",
                        "France"
                )
        ).isEqualTo("Paris");

        assertThat(server.requestCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should cache verification using normalized destination names")
    void cachesNormalizedDestinationNames() {
        server.respond(
                200,
                listJson(validPlace("Odesa", "Ukraine"))
        );

        assertThat(
                verifier.verifyAndGetCanonicalDestination(
                        "Odesa",
                        "Ukraine"
                )
        ).isEqualTo("Odesa");

        assertThat(
                verifier.verifyAndGetCanonicalDestination(
                        "  ODESA  ",
                        " Ukraine "
                )
        ).isEqualTo("Odesa");

        assertThat(server.requestCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should cache verification using the canonical destination name")
    void cachesCanonicalDestinationName() {
        server.respond(
                200,
                listJson(
                        placeJson(
                                "city",
                                "city",
                                "Odesa",
                                "Odesa, Ukraine",
                                "Ukraine",
                                """
                                {
                                  "name:en": "Odesa",
                                  "alt_name": "Odessa"
                                }
                                """
                        )
                )
        );

        assertThat(
                verifier.verifyAndGetCanonicalDestination(
                        "Odessa",
                        "Ukraine"
                )
        ).isEqualTo("Odesa");

        assertThat(
                verifier.verifyAndGetCanonicalDestination(
                        "Odesa",
                        "Ukraine"
                )
        ).isEqualTo("Odesa");

        assertThat(server.requestCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should normalize country aliases in verification cache keys")
    void cachesCountryAliasesUsingNormalizedKeys() {
        server.respond(
                200,
                listJson(validPlace("Prague", "Czechia"))
        );

        assertThat(
                verifier.verifyAndGetCanonicalDestination(
                        "Prague",
                        "Czech Republic"
                )
        ).isEqualTo("Prague");

        assertThat(
                verifier.verifyAndGetCanonicalDestination(
                        "Prague",
                        "Czechia"
                )
        ).isEqualTo("Prague");

        assertThat(server.requestCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should avoid duplicate HTTP requests for concurrent verification of the same destination")
    void avoidsDuplicateConcurrentRequests() throws Exception {
        server.respond(
                200,
                listJson(validPlace("Paris", "France"))
        );

        ExecutorService executor =
                Executors.newFixedThreadPool(5);

        try {
            List<Callable<String>> tasks = new ArrayList<>();

            for (int i = 0; i < 5; i++) {
                tasks.add(() ->
                        verifier.verifyAndGetCanonicalDestination(
                                "Paris",
                                "France"
                        )
                );
            }

            List<Future<String>> results =
                    executor.invokeAll(tasks);

            for (Future<String> result : results) {
                assertThat(result.get(5, TimeUnit.SECONDS))
                        .isEqualTo("Paris");
            }

            assertThat(server.requestCount()).isEqualTo(1);

        } finally {
            executor.shutdownNow();
        }
    }

    // --------------------------------------------------
    // Rate limiter and interruption
    // --------------------------------------------------

    @Test
    @DisplayName("Should restore the interrupt flag when rate-limit waiting is interrupted")
    void restoresInterruptFlagWhenRateLimitWaitIsInterrupted()
            throws ReflectiveOperationException {

        Field field =
                DestinationCountryVerifier.class.getDeclaredField(
                        "lastRequestStartedAtNanos"
                );

        field.setAccessible(true);

        // Simulate a request that started shortly before this verification.
        field.setLong(
                verifier,
                System.nanoTime() - TimeUnit.MILLISECONDS.toNanos(100)
        );

        Thread.currentThread().interrupt();

        try {
            assertThatThrownBy(
                    () -> verifier.verifyAndGetCanonicalDestination(
                            "Paris",
                            "France"
                    )
            )
                    .isInstanceOf(InvalidAiResponseException.class)
                    .hasMessageContaining(
                            "Destination verification was interrupted"
                    );

            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            assertThat(server.requestCount()).isZero();

        } finally {
            // Do not leak the interrupt flag into other tests.
            Thread.interrupted();
        }
    }

    // --------------------------------------------------
    // Test helpers
    // --------------------------------------------------

    private static String validPlace(
            String name,
            String country
    ) {
        return placeJson(
                "city",
                "city",
                name,
                name + ", Test Region, " + country,
                country,
                "{}"
        );
    }

    private static String placeJson(
            String type,
            String addressType,
            String name,
            String displayName,
            String country,
            String namedetailsJson
    ) {
        String addressJson = """
                {
                  "country": %s
                }
                """.formatted(jsonValue(country));

        return placeJsonWithAddress(
                type,
                addressType,
                name,
                displayName,
                addressJson,
                namedetailsJson
        );
    }

    private static String placeJsonWithAddress(
            String type,
            String addressType,
            String name,
            String displayName,
            String addressJson,
            String namedetailsJson
    ) {
        return """
                {
                  "type": %s,
                  "addresstype": %s,
                  "name": %s,
                  "display_name": %s,
                  "address": %s,
                  "namedetails": %s
                }
                """.formatted(
                jsonValue(type),
                jsonValue(addressType),
                jsonValue(name),
                jsonValue(displayName),
                addressJson,
                namedetailsJson
        );
    }

    private static String jsonValue(String value) {
        if (value == null) {
            return "null";
        }

        return "\""
                + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                + "\"";
    }

    private static String listJson(String... places) {
        return "[\n" + String.join(",\n", places) + "\n]";
    }

    // --------------------------------------------------
    // Local HTTP server
    // --------------------------------------------------

    private static final class StubGeocodingServer
            implements AutoCloseable {

        private final HttpServer httpServer;

        private final AtomicReference<HttpResponse> response =
                new AtomicReference<>(
                        new HttpResponse(200, "[]")
                );

        private final AtomicInteger requestCount =
                new AtomicInteger();

        private final AtomicBoolean stopped =
                new AtomicBoolean();

        private volatile URI lastUri;
        private volatile String lastMethod;
        private volatile String lastUserAgent;
        private volatile String lastAccept;

        private StubGeocodingServer() throws IOException {
            httpServer = HttpServer.create(
                    new InetSocketAddress("127.0.0.1", 0),
                    0
            );

            httpServer.createContext("/", exchange -> {
                requestCount.incrementAndGet();

                lastUri = exchange.getRequestURI();
                lastMethod = exchange.getRequestMethod();
                lastUserAgent =
                        exchange.getRequestHeaders().getFirst("User-Agent");
                lastAccept =
                        exchange.getRequestHeaders().getFirst("Accept");

                HttpResponse configuredResponse = response.get();

                byte[] body = configuredResponse.body()
                        .getBytes(StandardCharsets.UTF_8);

                try {
                    // Important: explicitly declare UTF-8 for JSON responses.
                    exchange.getResponseHeaders().set(
                            "Content-Type",
                            "application/json; charset=UTF-8"
                    );

                    if (body.length == 0) {
                        exchange.sendResponseHeaders(
                                configuredResponse.status(),
                                -1
                        );
                    } else {
                        exchange.sendResponseHeaders(
                                configuredResponse.status(),
                                body.length
                        );

                        exchange.getResponseBody().write(body);
                    }

                } finally {
                    exchange.close();
                }
            });

            httpServer.start();
        }

        private String baseUrl() {
            return "http://127.0.0.1:"
                    + httpServer.getAddress().getPort();
        }

        private void respond(int status, String body) {
            response.set(new HttpResponse(status, body));
        }

        private int requestCount() {
            return requestCount.get();
        }

        private URI lastUri() {
            return lastUri;
        }

        private String lastMethod() {
            return lastMethod;
        }

        private String lastUserAgent() {
            return lastUserAgent;
        }

        private String lastAccept() {
            return lastAccept;
        }

        private void stop() {
            if (stopped.compareAndSet(false, true)) {
                httpServer.stop(0);
            }
        }

        @Override
        public void close() {
            stop();
        }

        private record HttpResponse(
                int status,
                String body
        ) {
        }
    }
}