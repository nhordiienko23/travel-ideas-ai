
package com.github.nhordiienko23.travelideasai.service;

import com.github.nhordiienko23.travelideasai.exception.InvalidAiResponseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.text.Normalizer;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
public class DestinationCountryVerifier {

    private static final Logger log =
            LoggerFactory.getLogger(DestinationCountryVerifier.class);

    private static final long MIN_REQUEST_INTERVAL_NANOS =
            TimeUnit.SECONDS.toNanos(1);

    private static final Set<String> ALLOWED_SETTLEMENT_TYPES =
            Set.of("city", "town");

    private final RestClient restClient;
    private final JsonMapper jsonMapper;

    private final Map<String, VerifiedDestination> verificationCache =
            new ConcurrentHashMap<>();

    private long lastRequestStartedAtNanos;

    public DestinationCountryVerifier(
            JsonMapper jsonMapper,
            @Value("${app.geocoding.nominatim.base-url}")
            String baseUrl,
            @Value("${app.geocoding.nominatim.user-agent}")
            String userAgent,
            @Value("${app.geocoding.nominatim.connect-timeout:2s}")
            Duration connectTimeout,
            @Value("${app.geocoding.nominatim.read-timeout:3s}")
            Duration readTimeout
    ) {
        this.jsonMapper = jsonMapper;

        SimpleClientHttpRequestFactory requestFactory =
                new SimpleClientHttpRequestFactory();

        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader("User-Agent", userAgent)
                .defaultHeader("Accept", "application/json")
                .build();
    }

    public void verify(String destination, String aiCountry) {
        verifyAndGetCanonicalDestination(destination, aiCountry);
    }

    public String verifyAndGetCanonicalDestination(
            String destination,
            String aiCountry
    ) {
        if (destination == null || destination.isBlank()
                || aiCountry == null || aiCountry.isBlank()) {

            throw new InvalidAiResponseException(
                    "Destination and country are required for verification."
            );
        }

        VerifiedDestination verifiedDestination =
                resolveDestination(destination.strip(), aiCountry.strip());

        return verifiedDestination.canonicalName();
    }

    private synchronized VerifiedDestination resolveDestination(
            String destination,
            String expectedCountry
    ) {
        String cacheKey = buildCacheKey(destination, expectedCountry);

        VerifiedDestination cached = verificationCache.get(cacheKey);

        if (cached != null) {
            return cached;
        }

        waitForRateLimit();

        String responseBody;

        try {
            responseBody = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/search")
                            .queryParam(
                                    "q",
                                    destination + ", " + expectedCountry
                            )
                            .queryParam("featureType", "settlement")
                            .queryParam("format", "jsonv2")
                            .queryParam("addressdetails", 1)
                            .queryParam("namedetails", 1)
                            .queryParam("limit", 5)
                            .queryParam("accept-language", "en")
                            .build())
                    .retrieve()
                    .body(String.class);

        } catch (RestClientException exception) {
            log.warn(
                    "Could not verify destination '{}' in '{}'.",
                    destination,
                    expectedCountry,
                    exception
            );

            throw new InvalidAiResponseException(
                    "Destination verification is temporarily unavailable. "
                            + "Please try again.",
                    exception
            );
        }

        if (responseBody == null || responseBody.isBlank()) {
            throw new InvalidAiResponseException(
                    "Geographical service returned an empty response."
            );
        }

        List<?> results;

        try {
            Object parsedResponse =
                    jsonMapper.readValue(responseBody, Object.class);

            if (!(parsedResponse instanceof List<?> parsedList)) {
                throw new InvalidAiResponseException(
                        "Geographical service returned an invalid response."
                );
            }

            results = parsedList;

        } catch (JacksonException exception) {
            log.warn(
                    "Could not parse geographical data for '{}'.",
                    destination,
                    exception
            );

            throw new InvalidAiResponseException(
                    "Geographical service returned invalid data.",
                    exception
            );
        }

        if (results.isEmpty()) {
            throw new InvalidAiResponseException(
                    "Destination and country could not be verified: "
                            + destination + ", " + expectedCountry
            );
        }

        boolean destinationFound = false;
        boolean countryFound = false;

        for (Object result : results) {
            if (!(result instanceof Map<?, ?> place)) {
                continue;
            }

            if (!isCityOrTown(place)) {
                continue;
            }

            if (!matchesDestination(place, destination)) {
                continue;
            }

            destinationFound = true;

            Map<?, ?> address = getMap(place, "address");

            if (address == null) {
                continue;
            }

            Object countryValue = address.get("country");

            if (!(countryValue instanceof String actualCountry)
                    || actualCountry.isBlank()) {
                continue;
            }

            countryFound = true;

            if (!normalizeCountry(actualCountry)
                    .equals(normalizeCountry(expectedCountry))) {
                continue;
            }

            String canonicalName =
                    resolveCanonicalName(place, destination);

            VerifiedDestination verifiedDestination =
                    new VerifiedDestination(
                            canonicalName,
                            actualCountry.strip()
                    );

            cacheVerification(
                    destination,
                    expectedCountry,
                    verifiedDestination
            );

            log.debug(
                    "Verified destination '{}' as '{}' in '{}'.",
                    destination,
                    canonicalName,
                    actualCountry
            );

            return verifiedDestination;
        }

        if (!destinationFound) {
            throw new InvalidAiResponseException(
                    "Destination could not be verified as a city or town: "
                            + destination
            );
        }

        if (!countryFound) {
            throw new InvalidAiResponseException(
                    "Could not confirm the country for destination: "
                            + destination
            );
        }

        log.warn(
                "Destination '{}' was not found in expected country '{}'.",
                destination,
                expectedCountry
        );

        throw new InvalidAiResponseException(
                "AI returned an incorrect country for destination: "
                        + destination
        );
    }

    private boolean isCityOrTown(Map<?, ?> place) {
        String type = getString(place, "type")
                .toLowerCase(Locale.ROOT);

        String addressType = getString(place, "addresstype")
                .toLowerCase(Locale.ROOT);

        return ALLOWED_SETTLEMENT_TYPES.contains(type)
                || ALLOWED_SETTLEMENT_TYPES.contains(addressType);
    }

    private void cacheVerification(
            String requestedDestination,
            String expectedCountry,
            VerifiedDestination verifiedDestination
    ) {
        verificationCache.put(
                buildCacheKey(requestedDestination, expectedCountry),
                verifiedDestination
        );

        verificationCache.put(
                buildCacheKey(
                        verifiedDestination.canonicalName(),
                        expectedCountry
                ),
                verifiedDestination
        );
    }

    private boolean matchesDestination(
            Map<?, ?> place,
            String requestedDestination
    ) {
        String requested =
                normalizePlaceName(requestedDestination);

        Set<String> candidateNames = new HashSet<>();

        addName(candidateNames, getString(place, "name"));

        addName(
                candidateNames,
                firstDisplayNamePart(getString(place, "display_name"))
        );

        Map<?, ?> namedetails = getMap(place, "namedetails");

        if (namedetails != null) {
            for (Map.Entry<?, ?> entry : namedetails.entrySet()) {
                if (!(entry.getKey() instanceof String key)) {
                    continue;
                }

                if (isNameField(key)) {
                    addNameValues(candidateNames, entry.getValue());
                }
            }
        }

        return candidateNames.contains(requested);
    }

    private boolean isNameField(String key) {
        return key.equals("name")
                || key.startsWith("name:")
                || key.equals("alt_name")
                || key.startsWith("alt_name:")
                || key.equals("old_name")
                || key.startsWith("old_name:")
                || key.equals("int_name")
                || key.startsWith("int_name:")
                || key.equals("official_name")
                || key.startsWith("official_name:")
                || key.equals("short_name")
                || key.startsWith("short_name:")
                || key.equals("loc_name")
                || key.startsWith("loc_name:");
    }

    private void addNameValues(
            Set<String> names,
            Object value
    ) {
        if (value instanceof String string) {
            for (String name : string.split(";")) {
                addName(names, name);
            }
            return;
        }

        if (value instanceof Iterable<?> values) {
            for (Object item : values) {
                if (item instanceof String string) {
                    addName(names, string);
                }
            }
        }
    }

    private void addName(Set<String> names, String name) {
        if (name == null || name.isBlank()) {
            return;
        }

        String normalized = normalizePlaceName(name);

        if (!normalized.isBlank()) {
            names.add(normalized);
        }
    }

    private String resolveCanonicalName(
            Map<?, ?> place,
            String requestedDestination
    ) {
        Map<?, ?> namedetails = getMap(place, "namedetails");

        if (namedetails != null) {
            String englishName = firstNameValue(
                    namedetails.get("name:en")
            );

            if (!englishName.isBlank()) {
                return englishName;
            }

            String internationalName = firstNameValue(
                    namedetails.get("int_name")
            );

            if (!internationalName.isBlank()) {
                return internationalName;
            }
        }

        String placeName = getString(place, "name").strip();

        if (!placeName.isBlank()) {
            return placeName;
        }

        String displayName =
                firstDisplayNamePart(getString(place, "display_name"));

        if (!displayName.isBlank()) {
            return displayName;
        }

        return requestedDestination;
    }

    private String firstNameValue(Object value) {
        if (value instanceof String string) {
            return string.split(";", 2)[0].strip();
        }

        if (value instanceof Iterable<?> values) {
            for (Object item : values) {
                if (item instanceof String string && !string.isBlank()) {
                    return string.strip();
                }
            }
        }

        return "";
    }

    private Map<?, ?> getMap(
            Map<?, ?> source,
            String key
    ) {
        Object value = source.get(key);

        if (value instanceof Map<?, ?> map) {
            return map;
        }

        return null;
    }

    private String getString(
            Map<?, ?> source,
            String key
    ) {
        Object value = source.get(key);

        return value instanceof String string ? string : "";
    }

    private String firstDisplayNamePart(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            return "";
        }

        return displayName.split(",", 2)[0].strip();
    }

    private String buildCacheKey(
            String destination,
            String country
    ) {
        return normalizePlaceName(destination)
                + "|"
                + normalizeCountry(country);
    }

    private void waitForRateLimit() {
        long now = System.nanoTime();

        if (lastRequestStartedAtNanos != 0) {
            long elapsed = now - lastRequestStartedAtNanos;

            long remaining =
                    MIN_REQUEST_INTERVAL_NANOS - elapsed;

            if (remaining > 0) {
                try {
                    TimeUnit.NANOSECONDS.sleep(remaining);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();

                    throw new InvalidAiResponseException(
                            "Destination verification was interrupted.",
                            exception
                    );
                }
            }
        }

        lastRequestStartedAtNanos = System.nanoTime();
    }

    private String normalizePlaceName(String value) {
        if (value == null) {
            return "";
        }

        String replaced = value.strip()
                .replace('Ł', 'L')
                .replace('ł', 'l')
                .replace('Ø', 'O')
                .replace('ø', 'o')
                .replace('Đ', 'D')
                .replace('đ', 'd')
                .replace("ß", "ss")
                .replace("Æ", "AE")
                .replace("æ", "ae")
                .replace("Œ", "OE")
                .replace("œ", "oe");

        String normalized = Normalizer.normalize(
                replaced,
                Normalizer.Form.NFD
        );

        return normalized
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .strip();
    }

    private String normalizeCountry(String country) {
        String normalized = normalizePlaceName(country);

        return switch (normalized) {
            case "republic of moldova",
                 "moldova republic of" -> "moldova";

            case "czech republic" -> "czechia";

            case "united states of america",
                 "usa" -> "united states";

            case "uk",
                 "great britain" -> "united kingdom";

            case "turkiye" -> "turkey";

            default -> normalized;
        };
    }

    private record VerifiedDestination(
            String canonicalName,
            String country
    ) {
    }
}
