package com.github.nhordiienko23.travelideasai.service;

import com.github.nhordiienko23.travelideasai.dto.AiTravelRecommendation;
import com.github.nhordiienko23.travelideasai.dto.MatchScoreResponse;
import com.github.nhordiienko23.travelideasai.dto.RecommendationRequest;
import com.github.nhordiienko23.travelideasai.model.CurrencyCode;
import com.github.nhordiienko23.travelideasai.model.Interest;
import com.github.nhordiienko23.travelideasai.model.TransportMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static com.github.nhordiienko23.travelideasai.model.Interest.*;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Match Score Service Tests")
class MatchScoreServiceTest {

    private final MatchScoreService service = new MatchScoreService();

    private final List<Interest> requested = List.of(NATURE, HISTORY, FOOD);

    // --------------------------------------------------
    // Reference scoring scenarios
    // --------------------------------------------------

    @Test
    @DisplayName("Should calculate all eight reference scoring scenarios correctly")
    void calculatesAllReferenceCases() {
        // 3/3 interests + budget OK + duration OK = 100
        assertScore(List.of(NATURE, HISTORY, FOOD),
                "90", 4, 50, 30, 20, 100);

        // 2/3 interests + budget OK + duration OK = 83
        assertScore(List.of(NATURE, HISTORY),
                "90", 4, 33, 30, 20, 83);

        // 1/3 interests + budget OK + duration OK = 67
        assertScore(List.of(NATURE),
                "90", 4, 17, 30, 20, 67);

        // 0/3 interests + budget OK + duration OK = 50
        assertScore(List.of(BEACH),
                "90", 4, 0, 30, 20, 50);

        // 3/3 interests + budget OVER + duration OK = 70
        assertScore(List.of(NATURE, HISTORY, FOOD),
                "110", 4, 50, 0, 20, 70);

        // 3/3 interests + budget OK + duration OVER = 80
        assertScore(List.of(NATURE, HISTORY, FOOD),
                "90", 6, 50, 30, 0, 80);

        // 2/3 interests + budget OVER + duration OVER = 33
        assertScore(List.of(NATURE, HISTORY),
                "110", 6, 33, 0, 0, 33);

        // 0/3 interests + budget OVER + duration OVER = 0
        assertScore(List.of(BEACH),
                "110", 6, 0, 0, 0, 0);
    }

    // --------------------------------------------------
    // Interests
    // --------------------------------------------------

    @Test
    @DisplayName("Should not increase interest score when duplicate interests are present")
    void duplicateInterestsCannotInflateScore() {
        RecommendationRequest request = request(
                List.of(NATURE, NATURE, HISTORY, FOOD),
                "100",
                5
        );

        AiTravelRecommendation recommendation = recommendation(
                List.of(NATURE, NATURE, HISTORY, BEACH),
                "90",
                4
        );

        assertFalse(request.isInterestsUnique());

        MatchScoreResponse score =
                service.calculateScore(request, recommendation);

        assertEquals(33, score.interestsScore());

        assertEquals(
                List.of(NATURE, HISTORY),
                service.findMatchingInterests(request, recommendation)
        );
    }

    @Test
    @DisplayName("Should ignore AI interest tags not selected by the user")
    void extraAiInterestsDoNotIncreaseScore() {
        RecommendationRequest request =
                request(requested, "100", 5);

        AiTravelRecommendation recommendationWithoutExtraTags =
                recommendation(
                        List.of(NATURE, HISTORY),
                        "90",
                        4
                );

        AiTravelRecommendation recommendationWithExtraTags =
                recommendation(
                        List.of(NATURE, HISTORY, BEACH, CULTURE),
                        "90",
                        4
                );

        MatchScoreResponse scoreWithoutExtraTags =
                service.calculateScore(request, recommendationWithoutExtraTags);

        MatchScoreResponse scoreWithExtraTags =
                service.calculateScore(request, recommendationWithExtraTags);

        assertEquals(33, scoreWithoutExtraTags.interestsScore());
        assertEquals(33, scoreWithExtraTags.interestsScore());

        assertEquals(
                scoreWithoutExtraTags.interestsScore(),
                scoreWithExtraTags.interestsScore()
        );

        assertEquals(
                List.of(NATURE, HISTORY),
                service.findMatchingInterests(
                        request,
                        recommendationWithExtraTags
                )
        );
    }

    @Test
    @DisplayName("Should return matching interests in the user's request order")
    void matchingInterestsFollowRequestOrder() {
        assertEquals(
                List.of(FOOD, NATURE),
                service.findMatchingInterests(
                        request(
                                List.of(FOOD, HISTORY, NATURE),
                                "100",
                                5
                        ),
                        recommendation(
                                List.of(NATURE, BEACH, FOOD),
                                "90",
                                4
                        )
                )
        );
    }

    // --------------------------------------------------
    // Budget and duration boundaries
    // --------------------------------------------------

    @Test
    @DisplayName("Should award budget and duration points at exact boundaries")
    void exactBudgetAndDurationBoundariesEarnPoints() {
        MatchScoreResponse score = service.calculateScore(
                request(requested, "100.00", 5),
                recommendation(List.of(NATURE), "100.0", 5)
        );

        assertEquals(30, score.budgetScore());
        assertEquals(20, score.durationScore());
        assertEquals(67, score.totalScore());
    }

    @Test
    @DisplayName("Should award zero budget points when estimated cost exceeds the budget")
    void budgetOverLimitDoesNotEarnPoints() {
        MatchScoreResponse score = service.calculateScore(
                request(requested, "100", 5),
                recommendation(List.of(NATURE), "100.01", 4)
        );

        assertEquals(0, score.budgetScore());
    }

    @Test
    @DisplayName("Should award zero duration points when suggested duration exceeds available days")
    void durationOverLimitDoesNotEarnPoints() {
        MatchScoreResponse score = service.calculateScore(
                request(requested, "100", 5),
                recommendation(List.of(NATURE), "90", 6)
        );

        assertEquals(0, score.durationScore());
    }

    // --------------------------------------------------
    // Invalid interests
    // --------------------------------------------------

    @Test
    @DisplayName("Should reject null or empty interest inputs")
    void rejectsNullOrEmptyInterestInputs() {
        RecommendationRequest validRequest =
                request(requested, "100", 5);

        AiTravelRecommendation validRecommendation =
                recommendation(List.of(NATURE), "90", 4);

        assertThrows(IllegalArgumentException.class,
                () -> service.calculateScore(null, validRecommendation));

        assertThrows(IllegalArgumentException.class,
                () -> service.calculateScore(validRequest, null));

        assertThrows(IllegalArgumentException.class,
                () -> service.findMatchingInterests(
                        request(null, "100", 5),
                        validRecommendation
                ));

        assertThrows(IllegalArgumentException.class,
                () -> service.findMatchingInterests(
                        request(List.of(), "100", 5),
                        validRecommendation
                ));

        assertThrows(IllegalArgumentException.class,
                () -> service.findMatchingInterests(
                        request(Arrays.asList(NATURE, null), "100", 5),
                        validRecommendation
                ));

        assertThrows(IllegalArgumentException.class,
                () -> service.findMatchingInterests(
                        validRequest,
                        recommendation(null, "90", 4)
                ));

        assertThrows(IllegalArgumentException.class,
                () -> service.findMatchingInterests(
                        validRequest,
                        recommendation(List.of(), "90", 4)
                ));

        assertThrows(IllegalArgumentException.class,
                () -> service.findMatchingInterests(
                        validRequest,
                        recommendation(Arrays.asList(NATURE, null), "90", 4)
                ));
    }

    // --------------------------------------------------
    // Invalid budget and duration inputs
    // --------------------------------------------------

    @Test
    @DisplayName("Should reject null, zero and negative budget values")
    void rejectsInvalidBudgetInputs() {

        // Null user budget
        assertThrows(IllegalArgumentException.class,
                () -> service.calculateScore(
                        request(requested, null, 5),
                        recommendation(List.of(NATURE), "90", 4)
                ));

        // Zero user budget
        assertThrows(IllegalArgumentException.class,
                () -> service.calculateScore(
                        request(requested, "0", 5),
                        recommendation(List.of(NATURE), "90", 4)
                ));

        // Negative user budget
        assertThrows(IllegalArgumentException.class,
                () -> service.calculateScore(
                        request(requested, "-100", 5),
                        recommendation(List.of(NATURE), "90", 4)
                ));

        // Null AI estimated cost
        assertThrows(IllegalArgumentException.class,
                () -> service.calculateScore(
                        request(requested, "100", 5),
                        recommendation(List.of(NATURE), null, 4)
                ));

        // Zero AI estimated cost
        assertThrows(IllegalArgumentException.class,
                () -> service.calculateScore(
                        request(requested, "100", 5),
                        recommendation(List.of(NATURE), "0", 4)
                ));

        // Negative AI estimated cost
        assertThrows(IllegalArgumentException.class,
                () -> service.calculateScore(
                        request(requested, "100", 5),
                        recommendation(List.of(NATURE), "-1", 4)
                ));
    }

    @Test
    @DisplayName("Should reject null, negative and out-of-range trip durations")
    void rejectsInvalidDurationInputs() {

        // Null user duration
        assertThrows(IllegalArgumentException.class,
                () -> service.calculateScore(
                        request(requested, "100", null),
                        recommendation(List.of(NATURE), "90", 4)
                ));

        // Zero user duration
        assertThrows(IllegalArgumentException.class,
                () -> service.calculateScore(
                        request(requested, "100", 0),
                        recommendation(List.of(NATURE), "90", 4)
                ));

        // Negative user duration
        assertThrows(IllegalArgumentException.class,
                () -> service.calculateScore(
                        request(requested, "100", -1),
                        recommendation(List.of(NATURE), "90", 4)
                ));

        // User duration exceeds maximum
        assertThrows(IllegalArgumentException.class,
                () -> service.calculateScore(
                        request(requested, "100", 31),
                        recommendation(List.of(NATURE), "90", 4)
                ));

        // Null AI duration
        assertThrows(IllegalArgumentException.class,
                () -> service.calculateScore(
                        request(requested, "100", 5),
                        recommendation(List.of(NATURE), "90", null)
                ));

        // Zero AI duration
        assertThrows(IllegalArgumentException.class,
                () -> service.calculateScore(
                        request(requested, "100", 5),
                        recommendation(List.of(NATURE), "90", 0)
                ));

        // Negative AI duration
        assertThrows(IllegalArgumentException.class,
                () -> service.calculateScore(
                        request(requested, "100", 5),
                        recommendation(List.of(NATURE), "90", -1)
                ));

        // AI duration exceeds maximum
        assertThrows(IllegalArgumentException.class,
                () -> service.calculateScore(
                        request(requested, "100", 5),
                        recommendation(List.of(NATURE), "90", 31)
                ));
    }

    // --------------------------------------------------
    // Total score consistency
    // --------------------------------------------------

    @Test
    @DisplayName("Should calculate total score as the sum of individual scores")
    void totalScoreEqualsSumOfIndividualScores() {
        MatchScoreResponse score = service.calculateScore(
                request(requested, "100", 5),
                recommendation(List.of(NATURE, HISTORY), "90", 4)
        );

        assertEquals(
                score.interestsScore()
                        + score.budgetScore()
                        + score.durationScore(),
                score.totalScore()
        );

        assertTrue(score.totalScore() >= 0);
        assertTrue(score.totalScore() <= 100);
    }

    // --------------------------------------------------
    // Test helpers
    // --------------------------------------------------

    private void assertScore(
            List<Interest> tags,
            String cost,
            int duration,
            int interests,
            int budget,
            int durationPoints,
            int total
    ) {
        assertEquals(
                new MatchScoreResponse(
                        interests,
                        budget,
                        durationPoints,
                        total
                ),
                service.calculateScore(
                        request(requested, "100", 5),
                        recommendation(tags, cost, duration)
                )
        );
    }

    private RecommendationRequest request(
            List<Interest> interests,
            String budget,
            Integer duration
    ) {
        return new RecommendationRequest(
                "Kyiv",
                interests,
                budget == null ? null : new BigDecimal(budget),
                CurrencyCode.USD,
                duration,
                TransportMode.TRAIN
        );
    }

    private AiTravelRecommendation recommendation(
            List<Interest> tags,
            String cost,
            Integer duration
    ) {
        return new AiTravelRecommendation(
                "Lviv",
                "Ukraine",
                "A historic city.",
                "Matches your interests.",
                tags,
                cost == null ? null : new BigDecimal(cost),
                duration
        );
    }
}