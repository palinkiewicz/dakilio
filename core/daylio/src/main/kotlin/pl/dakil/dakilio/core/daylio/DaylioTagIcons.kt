package pl.dakil.dakilio.core.daylio

/** Used when no keyword matches. */
const val DEFAULT_TAG_ICON = "label"

/**
 * Keyword → Dakilio symbol key, checked in order; the first keyword found in the label wins, so more
 * specific keywords come first. Every key must exist in `DakilioSymbols` (core:designsystem).
 */
private val KEYWORDS: List<Pair<String, String>> = listOf(
    // Emotions
    "happy" to "sentiment_very_satisfied",
    "excited" to "sentiment_excited",
    "grateful" to "volunteer_activism",
    "relaxed" to "sentiment_calm",
    "calm" to "sentiment_calm",
    "content" to "sentiment_content",
    "tired" to "bedtime",
    "unsure" to "psychology_alt",
    "bored" to "sentiment_neutral",
    "anxious" to "sentiment_worried",
    "angry" to "thunderstorm",
    "stress" to "sentiment_stressed",
    "sad" to "sentiment_sad",
    "love" to "favorite",
    // Sleep
    "good sleep" to "hotel",
    "medium sleep" to "hotel",
    "bad sleep" to "bedtime",
    "sleep" to "bedtime",
    "nap" to "bedtime",
    // Health
    "exercise" to "fitness_center",
    "workout" to "fitness_center",
    "gym" to "fitness_center",
    "eat healthy" to "nutrition",
    "healthy" to "nutrition",
    "water" to "water_drop",
    "walk" to "directions_walk",
    "run" to "directions_run",
    "bike" to "directions_bike",
    "cycling" to "directions_bike",
    "swim" to "pool",
    "medic" to "medication",
    "sick" to "sick",
    "doctor" to "local_hospital",
    "meditat" to "self_improvement",
    "yoga" to "self_improvement",
    // Hobbies
    "movie" to "movie",
    "tv" to "tv",
    "read" to "menu_book",
    "gaming" to "sports_esports",
    "game" to "sports_esports",
    "sport" to "sports_soccer",
    "music" to "music_note",
    "relax" to "spa",
    "photo" to "photo_camera",
    "art" to "brush",
    "draw" to "brush",
    "paint" to "brush",
    // Social
    "family" to "family_restroom",
    "friend" to "groups",
    "date" to "favorite",
    "party" to "celebration",
    "kindness" to "volunteer_activism",
    "donat" to "volunteer_activism",
    "gift" to "redeem",
    // Chores
    "shopping" to "shopping_bag",
    "clean" to "cleaning_services",
    "cook" to "skillet",
    "homemade" to "skillet",
    "laundry" to "laundry",
    "garden" to "yard",
    // Food
    "fast food" to "restaurant",
    "restaurant" to "restaurant",
    "delivery" to "restaurant",
    "sweets" to "cake",
    "coffee" to "local_cafe",
    "alcohol" to "local_bar",
    "beer" to "local_bar",
    // Weather
    "sunny" to "sunny",
    "cloud" to "cloud",
    "rain" to "rainy",
    "snow" to "weather_snowy",
    "heat" to "sunny",
    "storm" to "thunderstorm",
    "wind" to "air",
    // Places and other
    "work" to "work",
    "school" to "school",
    "study" to "school",
    "travel" to "flight",
    "trip" to "directions_car",
    "nature" to "forest",
    "hik" to "hiking",
    "beach" to "beach_access",
    "home" to "home",
    "pet" to "pets",
    "dog" to "pets",
    "cat" to "pets",
    "money" to "payments",
    "saving" to "savings",
)

/** A Dakilio icon for an imported Daylio activity, guessed from its English name. */
fun daylioTagIcon(label: String): String {
    val text = label.lowercase()
    return KEYWORDS.firstOrNull { (keyword, _) -> text.containsWordStart(keyword) }?.second ?: DEFAULT_TAG_ICON
}

/** True when [keyword] appears at the start of a word ("read" matches "reading", not "bread"). */
private fun String.containsWordStart(keyword: String): Boolean {
    var index = indexOf(keyword)
    while (index >= 0) {
        if (index == 0 || !this[index - 1].isLetterOrDigit()) return true
        index = indexOf(keyword, index + 1)
    }
    return false
}

/** Every key the table can return, for tests. */
internal val daylioTagIconKeys: Set<String> get() = KEYWORDS.mapTo(mutableSetOf()) { it.second } + DEFAULT_TAG_ICON
