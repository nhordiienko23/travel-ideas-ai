
import { useEffect, useRef, useState } from "react";

import {
    getSavedIdeas,
    updateSavedIdea,
    deleteSavedIdea,
} from "../services/travelApi";

import SavedIdeaCard from "./SavedIdeaCard";

const NOTIFICATION_DURATION = 4500;

function SavedIdeas({ refreshKey = 0 }) {
    const [ideas, setIdeas] = useState([]);
    const [isLoading, setIsLoading] = useState(true);
    const [updatingId, setUpdatingId] = useState(null);
    const [deletingId, setDeletingId] = useState(null);
    const [notification, setNotification] = useState(null);
    const [reloadKey, setReloadKey] = useState(0);

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

    useEffect(() => {
        let isActive = true;

        async function loadIdeas() {
            setIsLoading(true);

            try {
                const response = await getSavedIdeas();

                if (isActive) {
                    setIdeas(response);
                }
            } catch (error) {
                if (isActive) {
                    showNotification(
                        "error",
                        error.message || "Failed to load saved ideas."
                    );
                }
            } finally {
                if (isActive) {
                    setIsLoading(false);
                }
            }
        }

        loadIdeas();

        return () => {
            isActive = false;
        };
    }, [refreshKey, reloadKey]);

    async function handleUpdate(id, request) {
        setUpdatingId(id);
        setNotification(null);

        try {
            const updatedIdea = await updateSavedIdea(id, request);

            setIdeas((previousIdeas) =>
                previousIdeas.map((idea) =>
                    idea.id === id ? updatedIdea : idea
                )
            );

            showNotification(
                "success",
                "Saved idea updated successfully!"
            );

            return true;
        } catch (error) {
            showNotification(
                "error",
                error.message || "Failed to update saved idea."
            );

            return false;
        } finally {
            setUpdatingId(null);
        }
    }

    async function handleDelete(id) {
        setDeletingId(id);
        setNotification(null);

        try {
            await deleteSavedIdea(id);

            setIdeas((previousIdeas) =>
                previousIdeas.filter((idea) => idea.id !== id)
            );

            showNotification(
                "success",
                "Saved idea deleted successfully!"
            );

            return true;
        } catch (error) {
            showNotification(
                "error",
                error.message || "Failed to delete saved idea."
            );

            return false;
        } finally {
            setDeletingId(null);
        }
    }

    function handleRefresh() {
        setNotification(null);
        setReloadKey((previousKey) => previousKey + 1);
    }

    return (
        <section className="saved-ideas-section">
            <div className="saved-ideas-header">
                <div>
                    <h2>Saved Ideas</h2>
                    <p>Your personal collection of travel destinations.</p>
                </div>

                <button
                    type="button"
                    className="refresh-button"
                    onClick={handleRefresh}
                    disabled={isLoading}
                >
                    Refresh
                </button>
            </div>

            {notification && (
                <p
                    key={notification.id}
                    role={notification.type === "error" ? "alert" : "status"}
                    aria-live="polite"
                    className={`saved-operation-message saved-operation-${notification.type}`}
                >
                    {notification.message}
                </p>
            )}

            {isLoading && (
                <p className="saved-ideas-message">
                    Loading your saved ideas...
                </p>
            )}

            {!isLoading && ideas.length === 0 && (
                <div className="empty-saved-ideas">
                    <h3>No saved trips yet</h3>
                    <p>
                        Explore destinations and save the ones you like.
                        They will appear here.
                    </p>
                </div>
            )}

            {!isLoading && ideas.length > 0 && (
                <div className="saved-ideas-list">
                    {ideas.map((idea) => (
                        <SavedIdeaCard
                            key={idea.id}
                            idea={idea}
                            onUpdate={handleUpdate}
                            onDelete={handleDelete}
                            isUpdating={updatingId === idea.id}
                            isDeleting={deletingId === idea.id}
                        />
                    ))}
                </div>
            )}
        </section>
    );
}

export default SavedIdeas;
