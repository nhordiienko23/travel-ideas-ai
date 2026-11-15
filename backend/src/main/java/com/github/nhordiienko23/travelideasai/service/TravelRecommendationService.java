
package com.github.nhordiienko23.travelideasai.service;

import com.github.nhordiienko23.travelideasai.dto.AiRecommendationBatch;
import com.github.nhordiienko23.travelideasai.dto.AiTravelRecommendation;
import com.github.nhordiienko23.travelideasai.dto.MatchScoreResponse;
import com.github.nhordiienko23.travelideasai.dto.RecommendationBatchResponse;
import com.github.nhordiienko23.travelideasai.dto.RecommendationRequest;
import com.github.nhordiienko23.travelideasai.dto.RecommendationResponse;
import com.github.nhordiienko23.travelideasai.exception.InvalidAiResponseException;
import com.github.nhordiienko23.travelideasai.model.Interest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class TravelRecommendationService {

    private static final Logger log =
            LoggerFactory.getLogger(TravelRecommendationService.class);

    private static final int REQUIRED_RECOMMENDATION_COUNT = 3;

    private static final BigDecimal COST_INCREMENT =
            BigDecimal.valueOf(5);

    private static final int MAX_COST_INTEGER_DIGITS = 10;

    private final TravelRecommendationAssistant assistant;
    private final MatchScoreService matchScoreService;
    private final DestinationCountryVerifier destinationCountryVerifier;
    private final Validator validator;

    public RecommendationBatchResponse generateRecommendations(
            RecommendationRequest request
    ) {
        if (request == null) {
            throw new IllegalArgumentException(
                    "Recommendation request is required."
            );
        }

        AiRecommendationBatch batch =
                assistant.generateRecommendations(request);

        validateBatch(batch);

        List<AiTravelRecommendation> canonicalRecommendations =
                canonicalizeDestinations(batch.recommendations());

        validateUniqueDestinations(canonicalRecommendations);

        List<RecommendationResponse> recommendations =
                new ArrayList<>(REQUIRED_RECOMMENDATION_COUNT);

        for (AiTravelRecommendation recommendation :
                canonicalRecommendations) {

            AiTravelRecommendation filteredRecommendation =
                    filterSelectedInterests(request, recommendation);

            BigDecimal normalizedCost =
                    normalizeEstimatedTotalCost(
                            recommendation.destination(),
                            recommendation.estimatedTotalCost()
                    );

            AiTravelRecommendation scoringRecommendation =
                    copyWithCost(
                            filteredRecommendation,
                            normalizedCost
                    );

            List<Interest> matchingInterests =
                    matchScoreService.findMatchingInterests(
                            request,
                            scoringRecommendation
                    );

            MatchScoreResponse score =
                    matchScoreService.calculateScore(
                            request,
                            scoringRecommendation
                    );

            String whyRecommended = resolveWhyRecommended(
                    recommendation,
                    filteredRecommendation,
                    matchingInterests
            );

            recommendations.add(new RecommendationResponse(
                    recommendation.destination(),
                    recommendation.country(),
                    recommendation.description(),
                    whyRecommended,
                    matchingInterests,
                    normalizedCost,
                    request.currency(),
                    recommendation.suggestedDurationDays(),
                    score
            ));
        }

        return new RecommendationBatchResponse(recommendations);
    }

    private List<AiTravelRecommendation> canonicalizeDestinations(
            List<AiTravelRecommendation> recommendations
    ) {
        List<AiTravelRecommendation> canonicalRecommendations =
                new ArrayList<>(recommendations.size());

        for (AiTravelRecommendation recommendation : recommendations) {

            String destination = normalizeDestination(
                    recommendation.destination(),
                    recommendation.country()
            );

            String canonicalDestination =
                    destinationCountryVerifier
                            .verifyAndGetCanonicalDestination(
                                    destination,
                                    recommendation.country()
                            );

            if (canonicalDestination == null
                    || canonicalDestination.isBlank()) {

                throw new InvalidAiResponseException(
                        "Geographical service did not return "
                                + "a valid canonical destination name."
                );
            }

            canonicalRecommendations.add(
                    new AiTravelRecommendation(
                            canonicalDestination,
                            recommendation.country(),
                            recommendation.description(),
                            recommendation.whyRecommended(),
                            recommendation.interestTags(),
                            recommendation.estimatedTotalCost(),
                            recommendation.suggestedDurationDays()
                    )
            );
        }

        return canonicalRecommendations;
    }

    private AiTravelRecommendation filterSelectedInterests(
            RecommendationRequest request,
            AiTravelRecommendation recommendation
    ) {
        Set<Interest> selectedInterests =
                new HashSet<>(request.interests());

        List<Interest> originalTags =
                recommendation.interestTags();

        List<Interest> filteredTags = originalTags.stream()
                .filter(selectedInterests::contains)
                .distinct()
                .toList();

        if (!filteredTags.equals(originalTags)) {
            log.warn(
                    "Ignoring unsupported or duplicate AI interest tags "
                            + "for destination '{}'. Returned tags: {}, "
                            + "selected interests: {}.",
                    recommendation.destination(),
                    originalTags,
                    request.interests()
            );
        }

        return new AiTravelRecommendation(
                recommendation.destination(),
                recommendation.country(),
                recommendation.description(),
                recommendation.whyRecommended(),
                filteredTags,
                recommendation.estimatedTotalCost(),
                recommendation.suggestedDurationDays()
        );
    }

    private AiTravelRecommendation copyWithCost(
            AiTravelRecommendation recommendation,
            BigDecimal estimatedTotalCost
    ) {
        return new AiTravelRecommendation(
                recommendation.destination(),
                recommendation.country(),
                recommendation.description(),
                recommendation.whyRecommended(),
                recommendation.interestTags(),
                estimatedTotalCost,
                recommendation.suggestedDurationDays()
        );
    }

    private BigDecimal normalizeEstimatedTotalCost(
            String destination,
            BigDecimal estimatedTotalCost
    ) {
        BigDecimal normalizedCost = estimatedTotalCost
                .divide(
                        COST_INCREMENT,
                        0,
                        RoundingMode.HALF_UP
                )
                .multiply(COST_INCREMENT)
                .setScale(2);

        if (normalizedCost.signum() <= 0) {
            throw new InvalidAiResponseException(
                    "AI returned an estimated cost that is too small "
                            + "to normalize safely."
            );
        }

        int integerDigits =
                normalizedCost.precision() - normalizedCost.scale();

        if (integerDigits > MAX_COST_INTEGER_DIGITS) {
            throw new InvalidAiResponseException(
                    "Normalized estimated cost exceeds the allowed precision."
            );
        }

        if (normalizedCost.compareTo(estimatedTotalCost) != 0) {
            log.info(
                    "Normalized estimated cost for '{}': {} -> {}.",
                    destination,
                    estimatedTotalCost,
                    normalizedCost
            );
        }

        return normalizedCost;
    }

    private String resolveWhyRecommended(
            AiTravelRecommendation originalRecommendation,
            AiTravelRecommendation filteredRecommendation,
            List<Interest> matchingInterests
    ) {
        boolean tagsWereFiltered =
                !originalRecommendation.interestTags()
                        .equals(filteredRecommendation.interestTags());

        if (matchingInterests.isEmpty()) {
            return "The AI could not identify a reliable match "
                    + "between this destination and your selected interests. "
                    + "Consider its description as general destination "
                    + "information rather than a personalized interest match.";
        }

        if (tagsWereFiltered) {
            return "The AI identified potential relevance to your selected "
                    + "interests in "
                    + formatInterests(matchingInterests)
                    + ". The original explanation was adjusted because "
                    + "some returned interest tags did not match your selection.";
        }

        return originalRecommendation.whyRecommended().strip();
    }

    private String formatInterests(List<Interest> interests) {
        List<String> names = interests.stream()
                .map(interest -> interest.name()
                        .toLowerCase(Locale.ROOT))
                .toList();

        if (names.size() == 1) {
            return names.getFirst();
        }

        return String.join(
                ", ",
                names.subList(0, names.size() - 1)
        ) + " and " + names.getLast();
    }

    private void validateBatch(AiRecommendationBatch batch) {
        if (batch == null || batch.recommendations() == null) {
            throw new InvalidAiResponseException(
                    "AI response must contain a recommendations list."
            );
        }

        if (batch.recommendations().size()
                != REQUIRED_RECOMMENDATION_COUNT) {

            throw new InvalidAiResponseException(
                    "AI must return exactly "
                            + REQUIRED_RECOMMENDATION_COUNT
                            + " recommendations."
            );
        }

        Set<ConstraintViolation<AiRecommendationBatch>> batchViolations =
                validator.validate(batch);

        if (!batchViolations.isEmpty()) {
            ConstraintViolation<AiRecommendationBatch> violation =
                    batchViolations.iterator().next();

            throw new InvalidAiResponseException(
                    "AI response validation failed: "
                            + violation.getPropertyPath()
                            + " "
                            + violation.getMessage()
            );
        }

        for (AiTravelRecommendation recommendation :
                batch.recommendations()) {

            if (recommendation == null) {
                throw new InvalidAiResponseException(
                        "AI recommendations must not contain null entries."
                );
            }

            Set<ConstraintViolation<AiTravelRecommendation>> violations =
                    validator.validate(recommendation);

            if (!violations.isEmpty()) {
                ConstraintViolation<AiTravelRecommendation> violation =
                        violations.iterator().next();

                throw new InvalidAiResponseException(
                        "AI recommendation validation failed: "
                                + violation.getPropertyPath()
                                + " "
                                + violation.getMessage()
                );
            }
        }
    }

    private void validateUniqueDestinations(
            List<AiTravelRecommendation> recommendations
    ) {
        Set<List<String>> destinations = new HashSet<>();

        for (AiTravelRecommendation recommendation : recommendations) {

            List<String> destinationKey = List.of(
                    normalizeForComparison(recommendation.destination()),
                    normalizeForComparison(recommendation.country())
            );

            if (!destinations.add(destinationKey)) {
                throw new InvalidAiResponseException(
                        "AI recommendations must have unique "
                                + "destination and country pairs."
                );
            }
        }
    }

    private String normalizeDestination(
            String destination,
            String country
    ) {
        String normalizedDestination = destination.strip();
        String normalizedCountry = country.strip();

        String countrySuffix = "(?i)(?:,\\s*|\\s+)"
                + Pattern.quote(normalizedCountry)
                + "\\s*$";

        String previousDestination;

        do {
            previousDestination = normalizedDestination;

            normalizedDestination = normalizedDestination
                    .replaceFirst(countrySuffix, "")
                    .strip();

        } while (!normalizedDestination.isBlank()
                && !normalizedDestination.equals(previousDestination));

        return normalizedDestination.isBlank()
                ? destination.strip()
                : normalizedDestination;
    }

    private String normalizeForComparison(String value) {
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
}
