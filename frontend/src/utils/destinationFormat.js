
export function normalizeDestinationName(destination, country) {
    let name = String(destination ?? "").trim();
    const countryName = String(country ?? "").trim();

    if (!name) {
        return countryName;
    }

    if (!countryName) {
        return name.replace(/,\s*$/, "");
    }

    // Remove the country suffix if the AI already included it.
    const escapedCountry = countryName.replace(
        /[.*+?^${}()|[\]\\]/g,
        "\\$&"
    );

    const countrySuffix = new RegExp(
        `[\\s,]+${escapedCountry}\\s*$`,
        "i"
    );

    name = name.replace(countrySuffix, "").trim();

    return name || countryName;
}

export function formatDestination(destination, country) {
    const name = normalizeDestinationName(destination, country);
    const countryName = String(country ?? "").trim();

    if (!name) {
        return countryName;
    }

    if (!countryName || name.toLowerCase() === countryName.toLowerCase()) {
        return name;
    }

    return `${name}, ${countryName}`;
}
