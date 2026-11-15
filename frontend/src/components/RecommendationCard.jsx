
import { formatDestination } from "../utils/destinationFormat";

function RecommendationCard({ recommendation, onSave, isSaving = false }) {
    const score = recommendation.matchScore;

    const formattedCost = Number(
        recommendation.estimatedTotalCost
    ).toLocaleString(undefined, {
        minimumFractionDigits: 2,
        maximumFractionDigits: 2,
    });

    const destinationName = formatDestination(
        recommendation.destination,
        recommendation.country
    );

    return (
        <article className="recommendation-card">
            <h3>{destinationName}</h3>

            <p>{recommendation.description}</p>

            <h4>Why we recommend it</h4>
            <p>{recommendation.whyRecommended}</p>

            <p>
                <strong>Estimated cost:</strong>{" "}
                {formattedCost} {recommendation.currency}
            </p>

            {score.budgetScore === 0 && (
                <p style={{ color: "#b45309" }}>
                    <strong>Budget notice:</strong> The estimated trip cost
                    exceeds your budget.
                </p>
            )}

            <p>
                <strong>Suggested duration:</strong>{" "}
                {recommendation.suggestedDurationDays} days
            </p>

            <p>
                <strong>Matching interests:</strong>{" "}
                {recommendation.matchingInterests.length > 0
                    ? recommendation.matchingInterests.join(", ")
                    : "No matching interests"}
            </p>

            <h4>Smart Trip Match</h4>

            <p>Interests: {score.interestsScore}/50</p>
            <p>Budget: {score.budgetScore}/30</p>
            <p>Duration: {score.durationScore}/20</p>

            <p>
                <strong>Total score: {score.totalScore}/100</strong>
            </p>

            <button
                type="button"
                onClick={() => onSave(recommendation)}
                disabled={isSaving}
            >
                {isSaving ? "Saving..." : "Save idea"}
            </button>
        </article>
    );
}

export default RecommendationCard;
