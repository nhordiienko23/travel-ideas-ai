const API_BASE_URL =
    import.meta.env.VITE_API_BASE_URL || "http://localhost:8080";

async function apiRequest(path, options = {}) {
    const response = await fetch(`${API_BASE_URL}${path}`, {
        ...options,
        headers: {
            ...(options.body ? { "Content-Type": "application/json" } : {}),
            ...options.headers,
        },
    });

    if (!response.ok) {
        let errorMessage = `Request failed (${response.status})`;

        try {
            const errorBody = await response.json();

            errorMessage =
                errorBody.detail ||
                errorBody.message ||
                errorBody.title ||
                errorMessage;
        } catch {
            // Response doesn't contain JSON.
        }

        throw new Error(errorMessage);
    }

    // HTTP 204 means success without a response body.
    if (response.status === 204) {
        return null;
    }

    return response.json();
}

// Generate three travel recommendations.
export function generateRecommendations(request) {
    return apiRequest("/api/recommendations", {
        method: "POST",
        body: JSON.stringify(request),
    });
}

// Save a recommended travel idea.
export function saveIdea(request) {
    return apiRequest("/api/ideas", {
        method: "POST",
        body: JSON.stringify(request),
    });
}

// Get all saved ideas.
export function getSavedIdeas() {
    return apiRequest("/api/ideas");
}

// Update the title and personal note of a saved idea.
export function updateSavedIdea(id, request) {
    return apiRequest(`/api/ideas/${id}`, {
        method: "PATCH",
        body: JSON.stringify(request),
    });
}

// Delete a saved idea.
export function deleteSavedIdea(id) {
    return apiRequest(`/api/ideas/${id}`, {
        method: "DELETE",
    });
}