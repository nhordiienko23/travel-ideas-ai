
import { useEffect, useState } from "react";

import {
    formatDestination,
    normalizeDestinationName,
} from "../utils/destinationFormat";

function SavedIdeaCard({
                           idea,
                           onUpdate,
                           onDelete,
                           isUpdating,
                           isDeleting,
                       }) {
    const [isEditing, setIsEditing] = useState(false);
    const [title, setTitle] = useState(idea.title);
    const [personalNote, setPersonalNote] = useState(
        idea.personalNote || ""
    );

    useEffect(() => {
        setTitle(idea.title);
        setPersonalNote(idea.personalNote || "");
    }, [idea.title, idea.personalNote]);

    async function handleUpdate(event) {
        event.preventDefault();

        if (!title.trim()) {
            return;
        }

        const updated = await onUpdate(idea.id, {
            title: title.trim(),
            personalNote: personalNote.trim() || null,
        });

        if (updated) {
            setIsEditing(false);
        }
    }

    function handleCancel() {
        setTitle(idea.title);
        setPersonalNote(idea.personalNote || "");
        setIsEditing(false);
    }

    async function handleDelete() {
        const confirmed = window.confirm(
            `Are you sure you want to delete "${idea.title}"?`
        );

        if (confirmed) {
            await onDelete(idea.id);
        }
    }

    const destinationName = formatDestination(
        idea.destination,
        idea.country
    );

    const defaultTitle = normalizeDestinationName(
        idea.destination,
        idea.country
    );

    const isDefaultTitle =
        idea.title.trim().toLowerCase() ===
        idea.destination.trim().toLowerCase() ||
        idea.title.trim().toLowerCase() ===
        destinationName.toLowerCase();

    const displayTitle = isDefaultTitle
        ? defaultTitle
        : idea.title;

    const createdAt = idea.createdAt
        ? new Date(idea.createdAt)
        : null;

    const formattedDate =
        createdAt && !Number.isNaN(createdAt.getTime())
            ? createdAt.toLocaleDateString()
            : null;

    const formattedCost = Number(
        idea.estimatedTotalCost
    ).toLocaleString(undefined, {
        minimumFractionDigits: 2,
        maximumFractionDigits: 2,
    });

    return (
        <article className="saved-idea-card">
            <div className="saved-idea-top">
                <div>
                    {isEditing ? (
                        <form onSubmit={handleUpdate} className="edit-idea-form">
                            <label htmlFor={`title-${idea.id}`}>Trip title</label>

                            <input
                                id={`title-${idea.id}`}
                                type="text"
                                value={title}
                                onChange={(event) => setTitle(event.target.value)}
                                maxLength={100}
                                required
                            />

                            <label htmlFor={`note-${idea.id}`}>
                                Personal note
                            </label>

                            <textarea
                                id={`note-${idea.id}`}
                                value={personalNote}
                                onChange={(event) =>
                                    setPersonalNote(event.target.value)
                                }
                                maxLength={500}
                                rows={3}
                                placeholder="Add your personal thoughts about this trip..."
                            />

                            <div className="saved-idea-actions">
                                <button
                                    type="submit"
                                    className="save-edit-button"
                                    disabled={isUpdating || isDeleting}
                                >
                                    {isUpdating ? "Saving..." : "Save changes"}
                                </button>

                                <button
                                    type="button"
                                    className="cancel-edit-button"
                                    onClick={handleCancel}
                                    disabled={isUpdating}
                                >
                                    Cancel
                                </button>
                            </div>
                        </form>
                    ) : (
                        <>
                            <h3>{displayTitle}</h3>

                            <p className="saved-idea-destination">
                                {destinationName}
                            </p>
                        </>
                    )}
                </div>

                {!isEditing && (
                    <span className="saved-score">
            {idea.matchScore}/100
          </span>
                )}
            </div>

            {!isEditing && (
                <>
                    <p className="saved-idea-description">
                        {idea.description}
                    </p>

                    <div className="saved-idea-details">
                        <div>
                            <span>Estimated cost</span>
                            <strong>
                                {formattedCost} {idea.currency}
                            </strong>
                        </div>

                        <div>
                            <span>Duration</span>
                            <strong>
                                {idea.suggestedDurationDays} days
                            </strong>
                        </div>

                        <div>
                            <span>Match score</span>
                            <strong>{idea.matchScore}/100</strong>
                        </div>
                    </div>

                    <div className="saved-idea-reason">
                        <h4>Why we recommended it</h4>
                        <p>{idea.whyRecommended}</p>
                    </div>

                    <div className="saved-idea-note">
                        <span>Personal note</span>
                        <p>
                            {idea.personalNote || "No personal note yet."}
                        </p>
                    </div>

                    {formattedDate && (
                        <p className="saved-idea-date">
                            Saved on {formattedDate}
                        </p>
                    )}

                    <div className="saved-idea-actions">
                        <button
                            type="button"
                            className="edit-idea-button"
                            onClick={() => setIsEditing(true)}
                            disabled={isDeleting}
                        >
                            Edit
                        </button>

                        <button
                            type="button"
                            className="delete-idea-button"
                            onClick={handleDelete}
                            disabled={isDeleting || isUpdating}
                        >
                            {isDeleting ? "Deleting..." : "Delete"}
                        </button>
                    </div>
                </>
            )}
        </article>
    );
}

export default SavedIdeaCard;
