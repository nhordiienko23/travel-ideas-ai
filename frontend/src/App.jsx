
import { useEffect, useRef, useState } from "react";

import TravelForm from "./components/TravelForm";
import RecommendationList from "./components/RecommendationList";
import SavedIdeas from "./components/SavedIdeas";

import {
  generateRecommendations,
  saveIdea,
  getSavedIdeas,
} from "./services/travelApi";

import { normalizeDestinationName } from "./utils/destinationFormat";

import "./App.css";

const NOTIFICATION_DURATION = 4500;

function normalizeText(value) {
  return String(value ?? "").trim().toLowerCase();
}

function isSameRecommendation(idea, recommendation) {
  return (
      normalizeText(
          normalizeDestinationName(idea.destination, idea.country)
      ) ===
      normalizeText(
          normalizeDestinationName(
              recommendation.destination,
              recommendation.country
          )
      ) &&
      normalizeText(idea.country) ===
      normalizeText(recommendation.country) &&
      normalizeText(idea.description) ===
      normalizeText(recommendation.description) &&
      normalizeText(idea.whyRecommended) ===
      normalizeText(recommendation.whyRecommended) &&
      Number(idea.estimatedTotalCost) ===
      Number(recommendation.estimatedTotalCost) &&
      normalizeText(idea.currency) ===
      normalizeText(recommendation.currency) &&
      Number(idea.suggestedDurationDays) ===
      Number(recommendation.suggestedDurationDays) &&
      Number(idea.matchScore) ===
      Number(recommendation.matchScore.totalScore)
  );
}

function App() {
  const [recommendations, setRecommendations] = useState([]);
  const [isLoading, setIsLoading] = useState(false);
  const [savingKey, setSavingKey] = useState(null);
  const [savedIdeasRefreshKey, setSavedIdeasRefreshKey] = useState(0);
  const [notification, setNotification] = useState(null);

  const notificationId = useRef(0);

  useEffect(() => {
    if (!notification) {
      return;
    }

    const timeoutId = setTimeout(() => {
      setNotification(null);
    }, NOTIFICATION_DURATION);

    return () => clearTimeout(timeoutId);
  }, [notification]);

  function showNotification(type, message) {
    notificationId.current += 1;

    setNotification({
      id: notificationId.current,
      type,
      message,
    });
  }

  async function handleGenerate(request) {
    setIsLoading(true);
    setNotification(null);
    setRecommendations([]);

    try {
      const response = await generateRecommendations(request);
      setRecommendations(response.recommendations);
    } catch (error) {
      showNotification(
          "error",
          error.message || "Failed to generate recommendations."
      );
    } finally {
      setIsLoading(false);
    }
  }

  function handleClearResults() {
    setRecommendations([]);
    setNotification(null);
  }

  async function handleSave(recommendation) {
    const key = `${recommendation.destination}-${recommendation.country}`;

    setSavingKey(key);
    setNotification(null);

    try {
      const existingIdeas = await getSavedIdeas();

      const alreadySaved = existingIdeas.some((idea) =>
          isSameRecommendation(idea, recommendation)
      );

      if (alreadySaved) {
        showNotification(
            "info",
            "This exact recommendation is already in your Saved Ideas."
        );
        return;
      }

      const destinationName = normalizeDestinationName(
          recommendation.destination,
          recommendation.country
      );

      const request = {
        title: destinationName,
        destination: destinationName,
        country: recommendation.country,
        description: recommendation.description,
        whyRecommended: recommendation.whyRecommended,
        estimatedTotalCost: recommendation.estimatedTotalCost,
        currency: recommendation.currency,
        suggestedDurationDays: recommendation.suggestedDurationDays,
        matchScore: recommendation.matchScore.totalScore,
        personalNote: null,
      };

      await saveIdea(request);

      showNotification(
          "success",
          `${destinationName} was successfully saved!`
      );

      setSavedIdeasRefreshKey((previousKey) => previousKey + 1);
    } catch (error) {
      showNotification(
          "error",
          error.message || "Failed to save the idea."
      );
    } finally {
      setSavingKey(null);
    }
  }

  return (
      <div className="app">
        <header className="app-header">
          <h1>Travel Ideas AI</h1>
          <p>Discover your next travel destination.</p>
        </header>

        <main>
          {notification && (
              <div
                  key={notification.id}
                  role={notification.type === "error" ? "alert" : "status"}
                  aria-live="polite"
                  className={`app-notification app-notification-${notification.type}`}
              >
                {notification.message}
              </div>
          )}

          <TravelForm
              onSubmit={handleGenerate}
              isLoading={isLoading}
          />

          {isLoading && (
              <p className="loading-message">
                Finding your perfect destinations...
              </p>
          )}

          {recommendations.length > 0 && (
              <div className="results-actions">
                <button
                    type="button"
                    className="clear-results-button"
                    onClick={handleClearResults}
                    disabled={savingKey !== null}
                >
                  Clear results
                </button>
              </div>
          )}

          <RecommendationList
              recommendations={recommendations}
              onSave={handleSave}
              isSaving={savingKey !== null}
          />

          <SavedIdeas refreshKey={savedIdeasRefreshKey} />
        </main>
      </div>
  );
}

export default App;
