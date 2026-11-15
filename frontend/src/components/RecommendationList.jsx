
import RecommendationCard from "./RecommendationCard";

function RecommendationList({
                                recommendations,
                                onSave,
                                isSaving = false,
                            }) {
    if (!recommendations || recommendations.length === 0) {
        return null;
    }

    return (
        <section className="recommendation-list">
            <h2>Recommended destinations</h2>

            {recommendations.map((recommendation) => (
                <RecommendationCard
                    key={`${recommendation.destination}-${recommendation.country}`}
                    recommendation={recommendation}
                    onSave={onSave}
                    isSaving={isSaving}
                />
            ))}
        </section>
    );
}

export default RecommendationList;
