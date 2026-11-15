package com.github.nhordiienko23.travelideasai.service;

import com.github.nhordiienko23.travelideasai.dto.AiRecommendationBatch;
import com.github.nhordiienko23.travelideasai.dto.AiTravelRecommendation;
import com.github.nhordiienko23.travelideasai.dto.MatchScoreResponse;
import com.github.nhordiienko23.travelideasai.dto.RecommendationBatchResponse;
import com.github.nhordiienko23.travelideasai.dto.RecommendationRequest;
import com.github.nhordiienko23.travelideasai.dto.RecommendationResponse;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Travel Recommendation Service Tests")
class TravelRecommendationServiceTest {

    private static final ValidatorFactory VALIDATOR_FACTORY =
            Validation.buildDefaultValidatorFactory();

    private static final Validator VALIDATOR =
            VALIDATOR_FACTORY.getValidator();

    @Mock
    private TravelRecommendationAssistant assistant;

    @Mock
    private MatchScoreService matchScoreService;

    @Mock
    private DestinationCountryVerifier destinationCountryVerifier;

    private TravelRecommendationService service;

    @BeforeEach
    void setUp() {
        service = new TravelRecommendationService(
                assistant,
                matchScoreService,
                destinationCountryVerifier,
                VALIDATOR
        );

        // By default, geographical verification returns the requested name.
        // Individual tests can override this behavior.
        lenient().when(
                destinationCountryVerifier.verifyAndGetCanonicalDestination(
                        anyString(),
                        anyString()
                )
        ).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterAll
    static void closeValidatorFactory() {
        VALIDATOR_FACTORY.close();
    }

    // --------------------------------------------------
    // Successful mapping
    // --------------------------------------------------

    @Test
    @DisplayName("Should map exactly three recommendations and delegate scoring")
    void mapsValidBatchAndDelegatesScoring() {
        RecommendationRequest request = request();

        AiRecommendationBatch aiBatch = batch(
                recommendation(
                        "Paris",
                        "France",
                        "Paris offers fascinating museums and historic landmarks."
                ),
                recommendation(
                        "Rome",
                        "Italy",
                        "Rome combines ancient architecture with Italian cuisine."
                ),
                recommendation(
                        "Kyoto",
                        "Japan",
                        "Kyoto is known for traditional temples and beautiful gardens."
                )
        );

        MatchScoreResponse score =
                new MatchScoreResponse(33, 30, 20, 83);

        List<Interest> matchingInterests =
                List.of(Interest.HISTORY, Interest.FOOD);

        when(assistant.generateRecommendations(request))
                .thenReturn(aiBatch);

        when(matchScoreService.calculateScore(any(), any()))
                .thenReturn(score);

        when(matchScoreService.findMatchingInterests(any(), any()))
                .thenReturn(matchingInterests);

        RecommendationBatchResponse response =
                service.generateRecommendations(request);

        assertThat(response.recommendations()).hasSize(3);

        assertThat(response.recommendations()).allSatisfy(item -> {
            assertThat(item.currency()).isEqualTo(request.currency());
            assertThat(item.matchScore()).isEqualTo(score);
            assertThat(item.matchingInterests())
                    .isEqualTo(matchingInterests);

            assertThat(item.estimatedTotalCost())
                    .isEqualByComparingTo("450.00");
        });

        RecommendationResponse first =
                response.recommendations().getFirst();

        assertThat(first.destination()).isEqualTo("Paris");
        assertThat(first.country()).isEqualTo("France");

        assertThat(first.description())
                .isEqualTo("A destination description.");

        assertThat(first.whyRecommended())
                .isEqualTo(
                        "Paris offers fascinating museums and historic landmarks."
                );

        assertThat(first.suggestedDurationDays()).isEqualTo(3);

        verify(assistant).generateRecommendations(request);

        verify(matchScoreService, times(3))
                .calculateScore(any(), any());

        verify(matchScoreService, times(3))
                .findMatchingInterests(any(), any());

        verify(destinationCountryVerifier, times(3))
                .verifyAndGetCanonicalDestination(anyString(), anyString());
    }

    @Test
    @DisplayName("Should preserve unique AI explanations for different destinations")
    void preservesUniqueAiExplanations() {
        RecommendationRequest request = request();

        String parisExplanation =
                "Paris offers world-famous museums and historic architecture.";

        String romeExplanation =
                "Rome allows travelers to explore ancient ruins and traditional Italian cuisine.";

        String kyotoExplanation =
                "Kyoto combines historic temples, traditional gardens and Japanese cultural experiences.";

        AiRecommendationBatch aiBatch = batch(
                recommendation("Paris", "France", parisExplanation),
                recommendation("Rome", "Italy", romeExplanation),
                recommendation("Kyoto", "Japan", kyotoExplanation)
        );

        when(assistant.generateRecommendations(request))
                .thenReturn(aiBatch);

        when(matchScoreService.calculateScore(any(), any()))
                .thenReturn(new MatchScoreResponse(33, 30, 20, 83));

        when(matchScoreService.findMatchingInterests(any(), any()))
                .thenReturn(List.of(Interest.HISTORY));

        RecommendationBatchResponse response =
                service.generateRecommendations(request);

        assertThat(response.recommendations())
                .extracting(RecommendationResponse::whyRecommended)
                .containsExactly(
                        parisExplanation,
                        romeExplanation,
                        kyotoExplanation
                );
    }

    @Test
    @DisplayName("Should use canonical destination names returned by geographical verification")
    void usesCanonicalDestinationNames() {
        RecommendationRequest request = request();

        AiRecommendationBatch aiBatch = batch(
                recommendation("Odessa", "Ukraine"),
                recommendation("Kiev", "Ukraine"),
                recommendation("Lvov", "Ukraine")
        );

        when(assistant.generateRecommendations(request))
                .thenReturn(aiBatch);

        when(destinationCountryVerifier.verifyAndGetCanonicalDestination(
                "Odessa", "Ukraine"
        )).thenReturn("Odesa");

        when(destinationCountryVerifier.verifyAndGetCanonicalDestination(
                "Kiev", "Ukraine"
        )).thenReturn("Kyiv");

        when(destinationCountryVerifier.verifyAndGetCanonicalDestination(
                "Lvov", "Ukraine"
        )).thenReturn("Lviv");

        when(matchScoreService.calculateScore(any(), any()))
                .thenReturn(new MatchScoreResponse(0, 0, 0, 0));

        when(matchScoreService.findMatchingInterests(any(), any()))
                .thenReturn(List.of());

        RecommendationBatchResponse response =
                service.generateRecommendations(request);

        assertThat(response.recommendations())
                .extracting(RecommendationResponse::destination)
                .containsExactly("Odesa", "Kyiv", "Lviv");
    }

    @Test
    @DisplayName("Should accept the correct country returned by geographical verification")
    void acceptsCorrectDestinationCountry() {
        RecommendationRequest request = request();

        AiRecommendationBatch aiBatch = batch(
                recommendation("Chernivtsi", "Ukraine"),
                recommendation("Lviv", "Ukraine"),
                recommendation("Kyiv", "Ukraine")
        );

        when(assistant.generateRecommendations(request))
                .thenReturn(aiBatch);

        when(matchScoreService.calculateScore(any(), any()))
                .thenReturn(new MatchScoreResponse(33, 30, 20, 83));

        when(matchScoreService.findMatchingInterests(any(), any()))
                .thenReturn(List.of(Interest.HISTORY));

        RecommendationBatchResponse response =
                service.generateRecommendations(request);

        assertThat(response.recommendations()).hasSize(3);

        verify(destinationCountryVerifier)
                .verifyAndGetCanonicalDestination(
                        "Chernivtsi",
                        "Ukraine"
                );
    }

    @Test
    @DisplayName("Should reject an incorrect country returned by AI")
    void rejectsIncorrectDestinationCountry() {
        RecommendationRequest request = request();

        AiRecommendationBatch aiBatch = batch(
                recommendation("Chernivtsi", "Moldova"),
                recommendation("Lviv", "Ukraine"),
                recommendation("Kyiv", "Ukraine")
        );

        when(assistant.generateRecommendations(request))
                .thenReturn(aiBatch);

        doThrow(new InvalidAiResponseException(
                "AI returned an incorrect country for a destination."
        )).when(destinationCountryVerifier)
                .verifyAndGetCanonicalDestination(
                        "Chernivtsi",
                        "Moldova"
                );

        assertThatThrownBy(
                () -> service.generateRecommendations(request)
        ).isInstanceOf(InvalidAiResponseException.class);

        verify(destinationCountryVerifier)
                .verifyAndGetCanonicalDestination(
                        "Chernivtsi",
                        "Moldova"
                );

        verifyNoInteractions(matchScoreService);
    }

    @Test
    @DisplayName("Should accept the same destination name in different countries")
    void acceptsSameDestinationInDifferentCountries() {
        RecommendationRequest request = request();

        AiRecommendationBatch aiBatch = batch(
                recommendation("Springfield", "United States"),
                recommendation("Springfield", "Canada"),
                recommendation("Springfield", "Australia")
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
    }

    @Test
    @DisplayName("Should normalize destination names containing the country")
    void normalizesDestinationNameContainingCountry() {
        RecommendationRequest request = request();

        AiRecommendationBatch aiBatch = batch(
                recommendation("Budapest, Hungary", "Hungary"),
                recommendation("Prague, Czech Republic", "Czech Republic"),
                recommendation("Vienna, Austria", "Austria")
        );

        when(assistant.generateRecommendations(request))
                .thenReturn(aiBatch);

        when(matchScoreService.calculateScore(any(), any()))
                .thenReturn(new MatchScoreResponse(0, 0, 0, 0));

        when(matchScoreService.findMatchingInterests(any(), any()))
                .thenReturn(List.of());

        RecommendationBatchResponse response =
                service.generateRecommendations(request);

        assertThat(response.recommendations())
                .extracting(RecommendationResponse::destination)
                .containsExactly("Budapest", "Prague", "Vienna");

        assertThat(response.recommendations())
                .extracting(RecommendationResponse::country)
                .containsExactly(
                        "Hungary",
                        "Czech Republic",
                        "Austria"
                );

        verify(destinationCountryVerifier)
                .verifyAndGetCanonicalDestination(
                        "Budapest",
                        "Hungary"
                );
    }

    // --------------------------------------------------
    // Cost normalization
    // --------------------------------------------------

    @Test
    @DisplayName("Should round estimated costs to the nearest multiple of five")
    void roundsEstimatedCosts() {
        RecommendationRequest request = request();

        AiRecommendationBatch aiBatch = batch(
                recommendationWithCost("Paris", "France", "452.50"),
                recommendationWithCost("Rome", "Italy", "456.75"),
                recommendationWithCost("Kyoto", "Japan", "699.99")
        );

        when(assistant.generateRecommendations(request))
                .thenReturn(aiBatch);

        when(matchScoreService.calculateScore(any(), any()))
                .thenReturn(new MatchScoreResponse(0, 30, 20, 50));

        when(matchScoreService.findMatchingInterests(any(), any()))
                .thenReturn(List.of());

        RecommendationBatchResponse response =
                service.generateRecommendations(request);

        assertThat(response.recommendations())
                .extracting(RecommendationResponse::estimatedTotalCost)
                .containsExactly(
                        new BigDecimal("455.00"),
                        new BigDecimal("455.00"),
                        new BigDecimal("700.00")
                );
    }

    @Test
    @DisplayName("Should pass the normalized cost to the scoring service")
    void passesNormalizedCostToScoringService() {
        RecommendationRequest request = request();

        AiRecommendationBatch aiBatch = batch(
                recommendationWithCost("Paris", "France", "452.50"),
                recommendationWithCost("Rome", "Italy", "600.00"),
                recommendationWithCost("Kyoto", "Japan", "700.00")
        );

        when(assistant.generateRecommendations(request))
                .thenReturn(aiBatch);

        when(matchScoreService.calculateScore(any(), any()))
                .thenReturn(new MatchScoreResponse(0, 30, 20, 50));

        when(matchScoreService.findMatchingInterests(any(), any()))
                .thenReturn(List.of());

        RecommendationBatchResponse response =
                service.generateRecommendations(request);

        ArgumentCaptor<AiTravelRecommendation> captor =
                ArgumentCaptor.forClass(AiTravelRecommendation.class);

        verify(matchScoreService, times(3))
                .calculateScore(eq(request), captor.capture());

        assertThat(captor.getAllValues().getFirst().estimatedTotalCost())
                .isEqualByComparingTo("455.00");

        assertThat(response.recommendations().getFirst().estimatedTotalCost())
                .isEqualByComparingTo("455.00");
    }

    @Test
    @DisplayName("Should reject a cost that becomes zero after normalization")
    void rejectsCostThatRoundsToZero() {
        RecommendationRequest request = request();

        AiRecommendationBatch aiBatch = batch(
                recommendationWithCost("Paris", "France", "0.01"),
                recommendation("Rome", "Italy"),
                recommendation("Kyoto", "Japan")
        );

        when(assistant.generateRecommendations(request))
                .thenReturn(aiBatch);

        assertThatThrownBy(
                () -> service.generateRecommendations(request)
        )
                .isInstanceOf(InvalidAiResponseException.class)
                .hasMessageContaining("too small");

        verifyNoInteractions(matchScoreService);
    }

    @Test
    @DisplayName("Should reject a normalized cost exceeding the allowed precision")
    void rejectsNormalizedCostExceedingPrecision() {
        RecommendationRequest request = request();

        AiRecommendationBatch aiBatch = batch(
                recommendationWithCost("Paris", "France", "9999999999.99"),
                recommendation("Rome", "Italy"),
                recommendation("Kyoto", "Japan")
        );

        when(assistant.generateRecommendations(request))
                .thenReturn(aiBatch);

        assertThatThrownBy(
                () -> service.generateRecommendations(request)
        )
                .isInstanceOf(InvalidAiResponseException.class)
                .hasMessageContaining("precision");

        verifyNoInteractions(matchScoreService);
    }

    // --------------------------------------------------
    // Interest filtering and explanations
    // --------------------------------------------------

    @Test
    @DisplayName("Should ignore unselected AI interests when calculating the score")
    void ignoresUnselectedInterests() {
        RecommendationRequest request = request();

        AiTravelRecommendation recommendationWithExtraTag =
                new AiTravelRecommendation(
                        "Paris",
                        "France",
                        "A destination description.",
                        "Paris offers historical landmarks and museums.",
                        List.of(
                                Interest.BEACH,
                                Interest.HISTORY,
                                Interest.FOOD
                        ),
                        new BigDecimal("450.00"),
                        3
                );

        AiRecommendationBatch aiBatch = batch(
                recommendationWithExtraTag,
                recommendation("Rome", "Italy"),
                recommendation("Kyoto", "Japan")
        );

        when(assistant.generateRecommendations(request))
                .thenReturn(aiBatch);

        when(matchScoreService.calculateScore(any(), any()))
                .thenReturn(new MatchScoreResponse(33, 30, 20, 83));

        when(matchScoreService.findMatchingInterests(any(), any()))
                .thenReturn(List.of(Interest.HISTORY, Interest.FOOD));

        RecommendationBatchResponse response =
                service.generateRecommendations(request);

        assertThat(response.recommendations()).hasSize(3);

        assertThat(response.recommendations().getFirst().whyRecommended())
                .isEqualTo(
                        "The AI identified potential relevance to your selected "
                                + "interests in history and food. "
                                + "The original explanation was adjusted because "
                                + "some returned interest tags did not match "
                                + "your selection."
                );

        ArgumentCaptor<AiTravelRecommendation> captor =
                ArgumentCaptor.forClass(AiTravelRecommendation.class);

        verify(matchScoreService, times(3))
                .calculateScore(eq(request), captor.capture());

        assertThat(captor.getAllValues().getFirst().interestTags())
                .containsExactly(
                        Interest.HISTORY,
                        Interest.FOOD
                );

        assertThat(
                response.recommendations().getFirst().matchingInterests()
        ).containsExactly(
                Interest.HISTORY,
                Interest.FOOD
        );
    }

    @Test
    @DisplayName("Should allow empty AI interest tags and return a neutral explanation")
    void acceptsEmptyInterestTags() {
        RecommendationRequest request = request();

        AiTravelRecommendation recommendationWithoutTags =
                recommendationWithTags(
                        "Paris",
                        "France",
                        List.of()
                );

        AiRecommendationBatch aiBatch = batch(
                recommendationWithoutTags,
                recommendation("Rome", "Italy"),
                recommendation("Kyoto", "Japan")
        );

        when(assistant.generateRecommendations(request))
                .thenReturn(aiBatch);

        when(matchScoreService.findMatchingInterests(any(), any()))
                .thenReturn(List.of());

        when(matchScoreService.calculateScore(any(), any()))
                .thenReturn(new MatchScoreResponse(0, 30, 20, 50));

        RecommendationBatchResponse response =
                service.generateRecommendations(request);

        RecommendationResponse first =
                response.recommendations().getFirst();

        assertThat(first.matchingInterests()).isEmpty();

        assertThat(first.whyRecommended())
                .isEqualTo(
                        "The AI could not identify a reliable match "
                                + "between this destination and your selected "
                                + "interests. Consider its description as general "
                                + "destination information rather than a "
                                + "personalized interest match."
                );

        assertThat(first.matchScore())
                .isEqualTo(new MatchScoreResponse(0, 30, 20, 50));
    }

    @Test
    @DisplayName("Should preserve the original explanation when all AI tags are selected")
    void preservesExplanationWhenAllTagsAreSelected() {
        RecommendationRequest request = request();

        String explanation =
                "Paris offers historical landmarks and traditional food experiences.";

        AiRecommendationBatch aiBatch = batch(
                recommendation("Paris", "France", explanation),
                recommendation("Rome", "Italy"),
                recommendation("Kyoto", "Japan")
        );

        when(assistant.generateRecommendations(request))
                .thenReturn(aiBatch);

        when(matchScoreService.findMatchingInterests(any(), any()))
                .thenReturn(List.of(Interest.HISTORY, Interest.FOOD));

        when(matchScoreService.calculateScore(any(), any()))
                .thenReturn(new MatchScoreResponse(33, 30, 20, 83));

        RecommendationBatchResponse response =
                service.generateRecommendations(request);

        assertThat(response.recommendations().getFirst().whyRecommended())
                .isEqualTo(explanation);
    }

    // --------------------------------------------------
    // Field boundaries
    // --------------------------------------------------

    @Test
    @DisplayName("Should accept valid maximum field lengths and duration")
    void acceptsValidFieldBoundaries() {
        RecommendationRequest request = request(30);

        AiRecommendationBatch aiBatch = batch(
                boundaryRecommendation("A"),
                boundaryRecommendation("B"),
                boundaryRecommendation("C")
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

        assertThat(response.recommendations().getFirst().estimatedTotalCost())
                .isEqualByComparingTo("9999999995.00");

        assertThat(response.recommendations().getFirst().suggestedDurationDays())
                .isEqualTo(30);
    }

    @Test
    @DisplayName("Should accept minimum field values that remain positive after cost normalization")
    void acceptsMinimumValidFieldValues() {
        RecommendationRequest request = request();

        AiTravelRecommendation minimumRecommendation =
                new AiTravelRecommendation(
                        "A",
                        "B",
                        "D",
                        "R",
                        List.of(Interest.HISTORY),
                        new BigDecimal("2.50"),
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
                .thenReturn(List.of(Interest.HISTORY));

        RecommendationBatchResponse response =
                service.generateRecommendations(request);

        assertThat(response.recommendations()).hasSize(3);

        RecommendationResponse first =
                response.recommendations().getFirst();

        assertThat(first.destination()).isEqualTo("A");
        assertThat(first.country()).isEqualTo("B");

        assertThat(first.estimatedTotalCost())
                .isEqualByComparingTo("5.00");

        assertThat(first.suggestedDurationDays()).isEqualTo(1);
        assertThat(first.whyRecommended()).isEqualTo("R");
    }

    // --------------------------------------------------
    // Invalid request
    // --------------------------------------------------

    @Test
    @DisplayName("Should reject a null request before calling dependencies")
    void rejectsNullRequest() {
        assertThatThrownBy(() -> service.generateRecommendations(null))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(
                assistant,
                matchScoreService,
                destinationCountryVerifier
        );
    }

    // --------------------------------------------------
    // Invalid AI batches
    // --------------------------------------------------

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidBatches")
    @DisplayName("Should reject invalid AI batches and recommendations")
    void rejectsInvalidBatch(
            String scenario,
            AiRecommendationBatch batch
    ) {
        when(assistant.generateRecommendations(any()))
                .thenReturn(batch);

        assertThatThrownBy(
                () -> service.generateRecommendations(request())
        ).isInstanceOf(InvalidAiResponseException.class);

        verify(assistant).generateRecommendations(any());

        verifyNoInteractions(matchScoreService);
    }

    private static Stream<Arguments> invalidBatches() {
        List<Arguments> cases = new ArrayList<>();

        cases.add(Arguments.of("null batch", null));

        cases.add(Arguments.of(
                "null recommendations list",
                new AiRecommendationBatch(null)
        ));

        cases.add(Arguments.of(
                "fewer than three recommendations",
                batch(
                        recommendation("Paris", "France"),
                        recommendation("Rome", "Italy")
                )
        ));

        cases.add(Arguments.of(
                "more than three recommendations",
                batch(
                        recommendation("Paris", "France"),
                        recommendation("Rome", "Italy"),
                        recommendation("Kyoto", "Japan"),
                        recommendation("Lima", "Peru")
                )
        ));

        cases.add(Arguments.of(
                "null recommendation item",
                batch(
                        recommendation("Paris", "France"),
                        null,
                        recommendation("Kyoto", "Japan")
                )
        ));

        cases.add(Arguments.of(
                "duplicate destination-country pair ignoring case and whitespace",
                batch(
                        recommendation("Paris", "France"),
                        recommendation("paris", " FRANCE "),
                        recommendation("Kyoto", "Japan")
                )
        ));

        cases.add(Arguments.of(
                "duplicate destination-country pair with country suffix",
                batch(
                        recommendation("Paris, France", "France"),
                        recommendation("Paris", "France"),
                        recommendation("Kyoto", "Japan")
                )
        ));

        cases.add(invalidRecommendation(
                "null destination",
                null, "France", "Description", "Reason",
                List.of(Interest.HISTORY), "20.00", 3
        ));

        cases.add(invalidRecommendation(
                "blank destination",
                "  ", "France", "Description", "Reason",
                List.of(Interest.HISTORY), "20.00", 3
        ));

        cases.add(invalidRecommendation(
                "destination over 100 characters",
                "D".repeat(101), "France", "Description", "Reason",
                List.of(Interest.HISTORY), "20.00", 3
        ));

        cases.add(invalidRecommendation(
                "null country",
                "Paris", null, "Description", "Reason",
                List.of(Interest.HISTORY), "20.00", 3
        ));

        cases.add(invalidRecommendation(
                "blank country",
                "Paris", " ", "Description", "Reason",
                List.of(Interest.HISTORY), "20.00", 3
        ));

        cases.add(invalidRecommendation(
                "country over 100 characters",
                "Paris", "C".repeat(101), "Description", "Reason",
                List.of(Interest.HISTORY), "20.00", 3
        ));

        cases.add(invalidRecommendation(
                "null description",
                "Paris", "France", null, "Reason",
                List.of(Interest.HISTORY), "20.00", 3
        ));

        cases.add(invalidRecommendation(
                "blank description",
                "Paris", "France", " ", "Reason",
                List.of(Interest.HISTORY), "20.00", 3
        ));

        cases.add(invalidRecommendation(
                "description over 1000 characters",
                "Paris", "France", "D".repeat(1001), "Reason",
                List.of(Interest.HISTORY), "20.00", 3
        ));

        cases.add(invalidRecommendation(
                "null recommendation reason",
                "Paris", "France", "Description", null,
                List.of(Interest.HISTORY), "20.00", 3
        ));

        cases.add(invalidRecommendation(
                "blank recommendation reason",
                "Paris", "France", "Description", " ",
                List.of(Interest.HISTORY), "20.00", 3
        ));

        cases.add(invalidRecommendation(
                "reason over 1000 characters",
                "Paris", "France", "Description", "R".repeat(1001),
                List.of(Interest.HISTORY), "20.00", 3
        ));

        cases.add(invalidRecommendation(
                "null interest tags",
                "Paris", "France", "Description", "Reason",
                null, "20.00", 3
        ));

        cases.add(invalidRecommendation(
                "null interest tag",
                "Paris", "France", "Description", "Reason",
                Arrays.asList(Interest.HISTORY, null), "20.00", 3
        ));

        cases.add(invalidRecommendation(
                "duplicate interest tags",
                "Paris", "France", "Description", "Reason",
                List.of(Interest.HISTORY, Interest.HISTORY), "20.00", 3
        ));

        cases.add(invalidRecommendation(
                "null estimated cost",
                "Paris", "France", "Description", "Reason",
                List.of(Interest.HISTORY), null, 3
        ));

        cases.add(invalidRecommendation(
                "zero estimated cost",
                "Paris", "France", "Description", "Reason",
                List.of(Interest.HISTORY), "0.00", 3
        ));

        cases.add(invalidRecommendation(
                "negative estimated cost",
                "Paris", "France", "Description", "Reason",
                List.of(Interest.HISTORY), "-1.00", 3
        ));

        cases.add(invalidRecommendation(
                "cost with more than 10 integer digits",
                "Paris", "France", "Description", "Reason",
                List.of(Interest.HISTORY), "10000000000.00", 3
        ));

        cases.add(invalidRecommendation(
                "cost with more than 2 fractional digits",
                "Paris", "France", "Description", "Reason",
                List.of(Interest.HISTORY), "20.001", 3
        ));

        cases.add(invalidRecommendation(
                "null suggested duration",
                "Paris", "France", "Description", "Reason",
                List.of(Interest.HISTORY), "20.00", null
        ));

        cases.add(invalidRecommendation(
                "duration below 1 day",
                "Paris", "France", "Description", "Reason",
                List.of(Interest.HISTORY), "20.00", 0
        ));

        cases.add(invalidRecommendation(
                "duration above 30 days",
                "Paris", "France", "Description", "Reason",
                List.of(Interest.HISTORY), "20.00", 31
        ));

        return cases.stream();
    }

    // --------------------------------------------------
    // Test helpers
    // --------------------------------------------------

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
                new BigDecimal("9999999994.99"),
                30
        );
    }

    private static AiTravelRecommendation recommendation(
            String destination,
            String country
    ) {
        return recommendation(
                destination,
                country,
                "It matches the requested interests."
        );
    }

    private static AiTravelRecommendation recommendation(
            String destination,
            String country,
            String reason
    ) {
        return new AiTravelRecommendation(
                destination,
                country,
                "A destination description.",
                reason,
                List.of(Interest.HISTORY, Interest.FOOD),
                new BigDecimal("450.00"),
                3
        );
    }

    private static AiTravelRecommendation recommendationWithCost(
            String destination,
            String country,
            String cost
    ) {
        return new AiTravelRecommendation(
                destination,
                country,
                "A destination description.",
                "Matches your interests.",
                List.of(Interest.HISTORY, Interest.FOOD),
                new BigDecimal(cost),
                3
        );
    }

    private static AiTravelRecommendation recommendationWithTags(
            String destination,
            String country,
            List<Interest> tags
    ) {
        return new AiTravelRecommendation(
                destination,
                country,
                "A destination description.",
                "A general destination explanation.",
                tags,
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
        return request(7);
    }

    private static RecommendationRequest request(int durationDays) {
        return new RecommendationRequest(
                "Kyiv",
                List.of(
                        Interest.HISTORY,
                        Interest.FOOD,
                        Interest.NATURE
                ),
                new BigDecimal("1000.00"),
                CurrencyCode.EUR,
                durationDays,
                TransportMode.TRAIN
        );
    }
}
