package com.github.nhordiienko23.travelideasai.service;

import com.github.nhordiienko23.travelideasai.dto.*;
import com.github.nhordiienko23.travelideasai.exception.InvalidAiResponseException;
import com.github.nhordiienko23.travelideasai.model.CurrencyCode;
import com.github.nhordiienko23.travelideasai.model.Interest;
import com.github.nhordiienko23.travelideasai.model.TransportMode;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Travel recommendation service")
class TravelRecommendationServiceTest {

    private static final ValidatorFactory VALIDATOR_FACTORY =
            Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR =
            VALIDATOR_FACTORY.getValidator();

    @Mock
    private TravelRecommendationAssistant assistant;

    @Mock
    private MatchScoreService matchScoreService;

    private TravelRecommendationService service;

    @BeforeEach
    void setUp() {
        service = new TravelRecommendationService(
                assistant,
                matchScoreService,
                VALIDATOR
        );
    }

    @AfterAll
    static void closeValidatorFactory() {
        VALIDATOR_FACTORY.close();
    }

    @Test
    @DisplayName("Maps exactly three valid recommendations and delegates scoring")
    void mapsValidBatchAndDelegatesScoring() {
        RecommendationRequest request = request();
        AiRecommendationBatch aiBatch = batch(
                recommendation("Paris", "France"),
                recommendation("Rome", "Italy"),
                recommendation("Kyoto", "Japan")
        );
        MatchScoreResponse score = new MatchScoreResponse(33, 30, 20, 83);
        List<Interest> matchingInterests =
                List.of(Interest.HISTORY, Interest.FOOD);

        when(assistant.generateRecommendations(request)).thenReturn(aiBatch);
        when(matchScoreService.calculateScore(any(), any())).thenReturn(score);
        when(matchScoreService.findMatchingInterests(any(), any()))
                .thenReturn(matchingInterests);

        RecommendationBatchResponse response =
                service.generateRecommendations(request);

        assertThat(response.recommendations()).hasSize(3);
        assertThat(response.recommendations()).allSatisfy(item -> {
            assertThat(item.currency()).isEqualTo(request.currency());
            assertThat(item.matchScore()).isEqualTo(score);
            assertThat(item.matchingInterests()).isEqualTo(matchingInterests);
            assertThat(item.estimatedTotalCost())
                    .isEqualByComparingTo("450.00");
        });

        assertThat(response.recommendations().getFirst().destination())
                .isEqualTo("Paris");
        assertThat(response.recommendations().getFirst().country())
                .isEqualTo("France");
        assertThat(response.recommendations().getFirst().description())
                .isEqualTo("A destination description.");
        assertThat(response.recommendations().getFirst().whyRecommended())
                .isEqualTo("It matches the requested interests.");
        assertThat(response.recommendations().getFirst().suggestedDurationDays())
                .isEqualTo(3);

        verify(assistant).generateRecommendations(request);
        verify(matchScoreService, times(3)).calculateScore(any(), any());
        verify(matchScoreService, times(3)).findMatchingInterests(any(), any());
    }

    @Test
    @DisplayName("Accepts destination names repeated in different countries")
    void acceptsSameDestinationInDifferentCountries() {
        RecommendationRequest request = request();
        AiRecommendationBatch aiBatch = batch(
                recommendation("Springfield", "United States"),
                recommendation("Springfield", "Canada"),
                recommendation("Springfield", "Australia")
        );

        when(assistant.generateRecommendations(request)).thenReturn(aiBatch);
        when(matchScoreService.calculateScore(any(), any()))
                .thenReturn(new MatchScoreResponse(0, 0, 0, 0));
        when(matchScoreService.findMatchingInterests(any(), any()))
                .thenReturn(List.of());

        RecommendationBatchResponse response =
                service.generateRecommendations(request);

        assertThat(response.recommendations()).hasSize(3);
    }

    @Test
    @DisplayName("Accepts maximum valid field lengths, cost precision, and duration")
    void acceptsValidFieldBoundaries() {
        RecommendationRequest request = request();
        AiRecommendationBatch aiBatch = batch(
                boundaryRecommendation("A"),
                boundaryRecommendation("B"),
                boundaryRecommendation("C")
        );

        when(assistant.generateRecommendations(request)).thenReturn(aiBatch);
        when(matchScoreService.calculateScore(any(), any()))
                .thenReturn(new MatchScoreResponse(0, 0, 0, 0));
        when(matchScoreService.findMatchingInterests(any(), any()))
                .thenReturn(List.of());

        RecommendationBatchResponse response =
                service.generateRecommendations(request);

        assertThat(response.recommendations()).hasSize(3);
        assertThat(response.recommendations().getFirst().estimatedTotalCost())
                .isEqualByComparingTo("9999999999.99");
        assertThat(response.recommendations().getFirst().suggestedDurationDays())
                .isEqualTo(30);
    }

    @Test
    @DisplayName("Rejects a null request before calling dependencies")
    void rejectsNullRequest() {
        assertThatThrownBy(() -> service.generateRecommendations(null))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(assistant, matchScoreService);
    }

    @Test
    @DisplayName("Accepts minimum valid field values")
    void acceptsMinimumValidFieldValues() {
        RecommendationRequest request = request();

        AiTravelRecommendation minimumRecommendation =
                new AiTravelRecommendation(
                        "A",
                        "B",
                        "D",
                        "R",
                        List.of(Interest.HISTORY),
                        new BigDecimal("0.01"),
                        1
                );

        AiRecommendationBatch aiBatch = batch(
                minimumRecommendation,
                recommendation("Rome", "Italy"),
                recommendation("Kyoto", "Japan")
        );

        when(assistant.generateRecommendations(request))
                .thenReturn(aiBatch);

        when(matchScoreService.calculateScore(any(), any()))
                .thenReturn(new MatchScoreResponse(0, 0, 0, 0));

        when(matchScoreService.findMatchingInterests(any(), any()))
                .thenReturn(List.of());

        RecommendationBatchResponse response =
                service.generateRecommendations(request);

        assertThat(response.recommendations()).hasSize(3);

        RecommendationResponse first =
                response.recommendations().getFirst();

        assertThat(first.destination()).isEqualTo("A");
        assertThat(first.country()).isEqualTo("B");
        assertThat(first.estimatedTotalCost())
                .isEqualByComparingTo("0.01");
        assertThat(first.suggestedDurationDays()).isEqualTo(1);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidBatches")
    @DisplayName("Rejects invalid AI batches and recommendations")
    void rejectsInvalidBatch(String scenario, AiRecommendationBatch batch) {
        when(assistant.generateRecommendations(request())).thenReturn(batch);

        assertThatThrownBy(() -> service.generateRecommendations(request()))
                .isInstanceOf(InvalidAiResponseException.class);

        verify(assistant).generateRecommendations(any());
        verifyNoInteractions(matchScoreService);
    }

    private static Stream<Arguments> invalidBatches() {
        List<Arguments> cases = new ArrayList<>();

        cases.add(Arguments.of("null batch", null));
        cases.add(Arguments.of("null recommendations list",
                new AiRecommendationBatch(null)));
        cases.add(Arguments.of("fewer than three recommendations",
                batch(recommendation("Paris", "France"),
                        recommendation("Rome", "Italy"))));
        cases.add(Arguments.of("more than three recommendations",
                batch(recommendation("Paris", "France"),
                        recommendation("Rome", "Italy"),
                        recommendation("Kyoto", "Japan"),
                        recommendation("Lima", "Peru"))));
        cases.add(Arguments.of("null recommendation item",
                batch(recommendation("Paris", "France"), null,
                        recommendation("Kyoto", "Japan"))));
        cases.add(Arguments.of("duplicate destination-country pair ignoring case and whitespace",
                batch(recommendation(" Paris ", "France"),
                        recommendation("paris", " FRANCE "),
                        recommendation("Kyoto", "Japan"))));

        cases.add(invalidRecommendation("null destination",
                null, "France", "Description", "Reason",
                List.of(Interest.HISTORY), "20.00", 3));
        cases.add(invalidRecommendation("blank destination",
                "  ", "France", "Description", "Reason",
                List.of(Interest.HISTORY), "20.00", 3));
        cases.add(invalidRecommendation("destination over 100 characters",
                "D".repeat(101), "France", "Description", "Reason",
                List.of(Interest.HISTORY), "20.00", 3));

        cases.add(invalidRecommendation("null country",
                "Paris", null, "Description", "Reason",
                List.of(Interest.HISTORY), "20.00", 3));
        cases.add(invalidRecommendation("blank country",
                "Paris", " ", "Description", "Reason",
                List.of(Interest.HISTORY), "20.00", 3));
        cases.add(invalidRecommendation("country over 100 characters",
                "Paris", "C".repeat(101), "Description", "Reason",
                List.of(Interest.HISTORY), "20.00", 3));

        cases.add(invalidRecommendation("null description",
                "Paris", "France", null, "Reason",
                List.of(Interest.HISTORY), "20.00", 3));
        cases.add(invalidRecommendation("blank description",
                "Paris", "France", " ", "Reason",
                List.of(Interest.HISTORY), "20.00", 3));
        cases.add(invalidRecommendation("description over 1000 characters",
                "Paris", "France", "D".repeat(1001), "Reason",
                List.of(Interest.HISTORY), "20.00", 3));

        cases.add(invalidRecommendation("null recommendation reason",
                "Paris", "France", "Description", null,
                List.of(Interest.HISTORY), "20.00", 3));
        cases.add(invalidRecommendation("blank recommendation reason",
                "Paris", "France", "Description", " ",
                List.of(Interest.HISTORY), "20.00", 3));
        cases.add(invalidRecommendation("reason over 1000 characters",
                "Paris", "France", "Description", "R".repeat(1001),
                List.of(Interest.HISTORY), "20.00", 3));

        cases.add(invalidRecommendation("null interest tags",
                "Paris", "France", "Description", "Reason",
                null, "20.00", 3));
        cases.add(invalidRecommendation("empty interest tags",
                "Paris", "France", "Description", "Reason",
                List.of(), "20.00", 3));
        cases.add(invalidRecommendation("null interest tag",
                "Paris", "France", "Description", "Reason",
                Arrays.asList(Interest.HISTORY, null), "20.00", 3));
        cases.add(invalidRecommendation("duplicate interest tags",
                "Paris", "France", "Description", "Reason",
                List.of(Interest.HISTORY, Interest.HISTORY), "20.00", 3));

        cases.add(invalidRecommendation("null estimated cost",
                "Paris", "France", "Description", "Reason",
                List.of(Interest.HISTORY), null, 3));
        cases.add(invalidRecommendation("zero estimated cost",
                "Paris", "France", "Description", "Reason",
                List.of(Interest.HISTORY), "0.00", 3));
        cases.add(invalidRecommendation("negative estimated cost",
                "Paris", "France", "Description", "Reason",
                List.of(Interest.HISTORY), "-1.00", 3));
        cases.add(invalidRecommendation("cost with more than 10 integer digits",
                "Paris", "France", "Description", "Reason",
                List.of(Interest.HISTORY), "10000000000.00", 3));
        cases.add(invalidRecommendation("cost with more than 2 fractional digits",
                "Paris", "France", "Description", "Reason",
                List.of(Interest.HISTORY), "20.001", 3));

        cases.add(invalidRecommendation("null suggested duration",
                "Paris", "France", "Description", "Reason",
                List.of(Interest.HISTORY), "20.00", null));
        cases.add(invalidRecommendation("duration below 1 day",
                "Paris", "France", "Description", "Reason",
                List.of(Interest.HISTORY), "20.00", 0));
        cases.add(invalidRecommendation("duration above 30 days",
                "Paris", "France", "Description", "Reason",
                List.of(Interest.HISTORY), "20.00", 31));

        return cases.stream();
    }

    private static Arguments invalidRecommendation(
            String scenario,
            String destination,
            String country,
            String description,
            String reason,
            List<Interest> tags,
            String cost,
            Integer duration
    ) {
        AiTravelRecommendation invalid = new AiTravelRecommendation(
                destination,
                country,
                description,
                reason,
                tags,
                cost == null ? null : new BigDecimal(cost),
                duration
        );

        return Arguments.of(
                scenario,
                batch(
                        invalid,
                        recommendation("Rome", "Italy"),
                        recommendation("Kyoto", "Japan")
                )
        );
    }

    private static AiTravelRecommendation boundaryRecommendation(
            String destinationSuffix
    ) {
        return new AiTravelRecommendation(
                "D".repeat(99) + destinationSuffix,
                "C".repeat(100),
                "D".repeat(1000),
                "R".repeat(1000),
                List.of(Interest.HISTORY),
                new BigDecimal("9999999999.99"),
                30
        );
    }

    private static AiTravelRecommendation recommendation(
            String destination,
            String country
    ) {
        return new AiTravelRecommendation(
                destination,
                country,
                "A destination description.",
                "It matches the requested interests.",
                List.of(Interest.HISTORY, Interest.FOOD),
                new BigDecimal("450.00"),
                3
        );
    }

    private static AiRecommendationBatch batch(
            AiTravelRecommendation... recommendations
    ) {
        return new AiRecommendationBatch(Arrays.asList(recommendations));
    }

    private static RecommendationRequest request() {
        return new RecommendationRequest(
                "Kyiv",
                List.of(Interest.HISTORY, Interest.FOOD, Interest.NATURE),
                new BigDecimal("1000.00"),
                CurrencyCode.EUR,
                7,
                TransportMode.TRAIN
        );
    }
}