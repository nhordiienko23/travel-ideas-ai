import { useState } from "react";

const AVAILABLE_INTERESTS = [
    "NATURE",
    "HISTORY",
    "FOOD",
    "CULTURE",
    "BEACH",
    "ADVENTURE",
    "ARCHITECTURE",
    "SHOPPING",
];

function TravelForm({ onSubmit, isLoading = false }) {
    const [departureCity, setDepartureCity] = useState("");
    const [interests, setInterests] = useState([]);
    const [budget, setBudget] = useState("");
    const [currency, setCurrency] = useState("USD");
    const [durationDays, setDurationDays] = useState("7");
    const [preferredTransport, setPreferredTransport] = useState("TRAIN");
    const [error, setError] = useState("");

    function handleInterestChange(interest) {
        setInterests((previousInterests) => {
            if (previousInterests.includes(interest)) {
                return previousInterests.filter((item) => item !== interest);
            }

            return [...previousInterests, interest];
        });
    }

    function handleClear() {
        setDepartureCity("");
        setInterests([]);
        setBudget("");
        setCurrency("PLN");
        setDurationDays("7");
        setPreferredTransport("TRAIN");
        setError("");
    }
    function handleSubmit(event) {
        event.preventDefault();
        setError("");

        if (interests.length === 0) {
            setError("Please select at least one interest.");
            return;
        }

        const request = {
            departureCity: departureCity.trim(),
            interests,
            budget: Number(budget),
            currency,
            durationDays: Number(durationDays),
            preferredTransport,
        };

        onSubmit(request);
    }

    return (
        <form onSubmit={handleSubmit} className="travel-form">
            <h2>Plan your trip</h2>

            <label htmlFor="departureCity">Departure city</label>
            <input
                id="departureCity"
                type="text"
                value={departureCity}
                onChange={(event) => setDepartureCity(event.target.value)}
                minLength={2}
                maxLength={100}
                placeholder="e.g. Warsaw"
                required
            />

            <fieldset>
                <legend>What are you interested in?</legend>

                <div className="interests-grid">
                    {AVAILABLE_INTERESTS.map((interest) => (
                        <label key={interest}>
                            <input
                                type="checkbox"
                                checked={interests.includes(interest)}
                                onChange={() => handleInterestChange(interest)}
                            />
                            {interest.charAt(0) + interest.slice(1).toLowerCase()}
                        </label>
                    ))}
                </div>
            </fieldset>

            <label htmlFor="budget">Your budget</label>
            <input
                id="budget"
                type="number"
                value={budget}
                onChange={(event) => setBudget(event.target.value)}
                min="0.01"
                max="9999999999.99"
                step="0.01"
                placeholder="e.g. 1500"
                required
            />

            <label htmlFor="currency">Currency</label>
            <select
                id="currency"
                value={currency}
                onChange={(event) => setCurrency(event.target.value)}
            >
                <option value="USD">USD</option>
                <option value="EUR">EUR</option>
                <option value="UAH">UAH</option>
                <option value="PLN">PLN</option>
            </select>

            <label htmlFor="durationDays">Trip duration (days)</label>
            <input
                id="durationDays"
                type="number"
                value={durationDays}
                onChange={(event) => setDurationDays(event.target.value)}
                min="1"
                max="30"
                step="1"
                required
            />

            <label htmlFor="preferredTransport">Preferred transport</label>
            <select
                id="preferredTransport"
                value={preferredTransport}
                onChange={(event) => setPreferredTransport(event.target.value)}
            >
                <option value="TRAIN">Train</option>
                <option value="BUS">Bus</option>
                <option value="CAR">Car</option>
                <option value="PLANE">Plane</option>
            </select>

            {error && <p role="alert" className="form-error">{error}</p>}

            <div className="form-actions">
                <button
                    type="button"
                    className="clear-button"
                    onClick={handleClear}
                    disabled={isLoading}
                >
                    Clear form
                </button>

                <button type="submit" disabled={isLoading}>
                    {isLoading ? "Finding destinations..." : "Find destinations"}
                </button>
            </div>
        </form>
    );
}

export default TravelForm;
