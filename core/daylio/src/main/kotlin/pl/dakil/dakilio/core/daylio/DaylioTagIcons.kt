package pl.dakil.dakilio.core.daylio

/** Used when neither the Daylio icon nor a keyword matches. */
const val DEFAULT_TAG_ICON = "label"

/**
 * Daylio icon id (a backup's `tags[].icon`) → Dakilio symbol key, for the icon the user picked in Daylio. Only
 * clear counterparts are listed, with Daylio's icon names in the comments; the rest fall back to the label
 * keywords. Every key must exist in `DakilioSymbols` (core:designsystem).
 */
private val ICONS: List<Pair<String, IntArray>> = listOf(
    // Weather
    // sun_2, sun, sun_symbol, summer, sun_glasses, sunbathe, sun_lounger
    "sunny" to intArrayOf(79, 242, 36598, 10027, 80, 31914, 31872),
    "cloud" to intArrayOf(24, 9257), // cloud, clouds
    "partly_cloudy_day" to intArrayOf(243, 9248), // partly_cloudy_day, partly_cloudy_rain
    // rain, downpour, light_rain, light_rain_2, moderate_rain, torrential_rain, heavy_rain, intense_rain,
    // rainy_weather, umbrella, wet
    "rainy" to intArrayOf(245, 9252, 9262, 18610, 18591, 18592, 18593, 18594, 10383, 2919, 9269),
    "weather_snowy" to intArrayOf(247, 9271, 9265, 9312, 10163, 130), // snow, light_snow, sleet, hail, snowman, winter
    "ac_unit" to intArrayOf(10158), // snowflake
    // cloud_lighting, storm, lightning_bolt, flash_on, tornado, hurricane
    "thunderstorm" to intArrayOf(246, 9310, 277, 16421, 9309, 9999991),
    "air" to intArrayOf(293, 37572, 17001), // wind, windy_weather, windsock
    "foggy" to intArrayOf(9999916), // fog
    // moon, crescent_moon, partly_cloudy_night, night_landscape, new_moon, full_moon
    "nights_stay" to intArrayOf(9256, 54382, 9260, 9013, 342, 346),
    // Health
    // barbell, weightlift, curls_with_dumbbells, bench_press, bench_press_with_dumbbells, deadlift, flex_biceps,
    // crossfit, pushups, pullups, squats, exercise, functional_training, personal_trainer, treadmill
    "fitness_center" to intArrayOf(
        6, 90, 9782, 9819, 9833, 62779, 179, 35960, 9760, 9810, 9771, 9769, 36927, 36917, 9794
    ),
    "directions_walk" to intArrayOf(278, 25731, 9861), // walking, nordic_walking, human_footprints
    "directions_run" to intArrayOf(67, 60522), // running, track_and_field
    // meditation_guru, yoga, guru, acroyoga, pranava, lotus, wise_mind
    "self_improvement" to intArrayOf(56, 336, 9808, 107557, 240, 35926, 56733),
    // healthy_food, healthy_eating, natural_food, organic_food, vegan_food, vegetarian_food, salad, carrot, apple,
    // broccoli
    "nutrition" to intArrayOf(36916, 67233, 15936, 15937, 8445, 8433, 56701, 194, 8348, 11312),
    "water_drop" to intArrayOf(89, 353), // water, sport_bottle
    // sneeze, coughing, runny_nose, fever, virus, coronavirus, vomited, medical_thermometer
    "sick" to intArrayOf(40788, 25219, 58837, 58834, 9512, 10565, 187, 9553),
    // pill, pills, prescription_pill_bottle, painkiller, syringe
    "medication" to intArrayOf(64, 60339, 33298, 9999942, 9565),
    // heart_monitor, heart_with_pulse, medical_heart, hypertension
    "monitor_heart" to intArrayOf(220, 35588, 9552, 66166),
    "pool" to intArrayOf(82, 117102, 25351), // swimming, lap_pool, water_polo
    // bicycle, cycling_road_v1, cycling_track, spinning, electric_bike
    "directions_bike" to intArrayOf(11, 47443, 9846, 36946, 90133),
    "sports_soccer" to intArrayOf(43, 44, 11631), // football, football2, sport
    // tennis, tennis_2, tennis_ball, ping_pong, badminton, squash_racquet
    "sports_tennis" to intArrayOf(207, 66955, 45732, 182, 24313, 35823),
    // sleeping_in_bed, bed, single_bed, sleep_good, sleep_medium, read_in_bed
    "hotel" to intArrayOf(70, 8021, 8038, 357, 356, 10811),
    // sleep, sleeping, sleep_early, napping, sleepy_eyes, undergo_insomnia
    "bedtime" to intArrayOf(10140, 7841, 355, 67226, 119977, 358),
    // Places
    "home" to intArrayOf(49, 151), // cottage_v1, living_room
    "apartment" to intArrayOf(10516, 111425), // building, city_buildings
    "location_city" to intArrayOf(25151), // city
    "store" to intArrayOf(18898, 78230), // small_business, online_store
    // hospital_3, ambulance, stethoscope, doctors_bag, medical_doctor, doctor_male, doctor_examination,
    // dentist_female
    "local_hospital" to intArrayOf(9508, 9541, 103, 104, 9570, 8969, 9999944, 122886),
    "church" to intArrayOf(142, 8732, 8793, 8688, 47281), // city_church, chapel, cathedral, basilica, orthodox_church
    // Nature & travel
    "explore" to intArrayOf(9733, 7698), // compass_v1, binoculars
    "hiking" to intArrayOf(9844, 9788), // trekking, climbing_helmet
    "forest" to intArrayOf(9856, 36, 121), // forest, deciduous_tree, coniferous_tree
    "park" to intArrayOf(8669, 8714), // park_concert_shell, city_bench
    "landscape" to intArrayOf(52, 49261), // landscape, mountain
    "beach_access" to intArrayOf(9, 137, 136), // beach_umbrella, beach, beach_ball
    // airport, airplane_take_off, airplane_landing, airplane_mode_on, prop_plane
    "flight" to intArrayOf(1, 9352, 12664, 12665, 9336),
    "directions_car" to intArrayOf(18, 16690, 16692, 37733, 9349, 9359), // car, sedan, suv, suv_2, steering_wheel, taxi
    "train" to intArrayOf(85, 38912, 9374, 75), // train, japanese_bullet_train, tram, steam_engine
    "location_on" to intArrayOf(152, 126), // marker, map_marker
    "map" to intArrayOf(7885, 8202), // map, treasure_map
    "travel_explore" to intArrayOf(125, 37118), // globe, globe_earth
    "signpost" to intArrayOf(129), // signpost
    "sailing" to intArrayOf(217), // sail_boat
    "camping" to intArrayOf(9899, 17, 300), // camping_tent, campfire, rv_campground
    // leaf, oak_leaf, sprout, recycle, recycling, sustainability
    "eco" to intArrayOf(10118, 10092, 10097, 335, 9137, 24763),
    // flower, rose, spa_flower, orchid, bridal_bouquet, potted_plant
    "local_florist" to intArrayOf(10126, 31947, 284, 63016, 10157, 19189),
    // Activities
    // briefcase, business, job, permanent_job, contract_job, workstation, businessman, businesswoman
    "work" to intArrayOf(2784, 16, 31945, 20397, 20398, 171, 11214, 11226),
    // class, graduation_cap, motarboard, university, students, classroom, homework, student_male_v1, student_female,
    // preschool
    "school" to intArrayOf(22, 11225, 250, 86, 77, 9486, 9999909, 38834, 40323, 67221),
    "menu_book" to intArrayOf(12, 7719, 38390, 38854, 9480), // book, reading, open_book, books, literature
    "sports_esports" to intArrayOf(30, 49777, 39938), // controller, nintendo_switch, nintendo_with_card
    // clapperboard, movie, cinema_, movie_projector, documentary, photo_reel
    "movie" to intArrayOf(143, 9010, 11523, 11139, 39, 37715),
    // musical_notes, music_record, classic_music, rock_music, metal_music, country_music, folk_music
    "music_note" to intArrayOf(60, 61, 224, 11076, 180, 11662, 11663),
    "headphones" to intArrayOf(288, 46321, 9418), // headphones, earbud_headphones, headset
    "skillet" to intArrayOf(31, 8536, 14, 20441), // cooker, cooking_pot, ingredients, chef_hat
    // restaurant_v1, meal, cutlery, restaurant_table, waiter, food_and_wine
    "restaurant" to intArrayOf(65, 55, 8349, 37113, 8463, 37485),
    "shopping_bag" to intArrayOf(68), // shopping_bag
    "cleaning_services" to intArrayOf(50, 139, 8468, 62156), // housekeeping, broom, vacuum_cleaner, sponge
    "brush" to intArrayOf(102, 155, 156, 352, 40314), // paint, paint_brush, paint_palette, pencil_drawing, easel
    "computer" to intArrayOf(29, 58, 11313), // laptop, monitor, pc_on_desk
    "tv" to intArrayOf(91, 66, 10850), // tv, retro_tv, watch_tv_in_bed
    "theater_comedy" to intArrayOf(83, 8913, 8927), // theatre_mask, comedy, drama
    "piano" to intArrayOf(9398, 92005), // piano, grand_piano
    "photo_camera" to intArrayOf(7211, 28, 2891), // camera, compact_camera, slr_large_lens
    // coffee_to_go, coffee, cafe, tea, tea_cup, espresso_cup, french_press, coffee_pot
    "local_cafe" to intArrayOf(27, 8415, 204, 200, 26399, 26401, 26398, 26400),
    // beer, cocktail, wine_bottle, wine_glass, beer_glass, beer_bottle, beer_can, champagne, bar
    "local_bar" to intArrayOf(10, 26, 92, 8336, 8331, 8341, 26113, 176, 12696),
    "cake" to intArrayOf(32, 8449, 117, 10149), // cupcake, birthday_cake, birthday, wedding_cake
    "laundry" to intArrayOf(88, 105268), // washing_machine, tumble_dryer
    "yard" to intArrayOf(82444, 89297, 10116, 10100), // yard_work, lawn_mower, watering_can, garden_shears
    // Objects
    "star" to intArrayOf(7856), // star
    "lightbulb" to intArrayOf(2979, 285), // idea, light_on
    "rocket_launch" to intArrayOf(9378, 11603), // rocket, launched_rocket
    "savings" to intArrayOf(144, 7977), // coins, money_bag
    "payments" to intArrayOf(87, 135, 134, 7991, 34054), // us_dollar, banknotes, bank_cards_v1, wallet, paycheque
    "shopping_cart" to intArrayOf(69, 8384), // shopping_cart, shopping_basket
    "redeem" to intArrayOf(123, 10132, 25103, 25379), // gift, christmas_gift, give_gift, birthday_presents
    "emoji_events" to intArrayOf(286, 10717, 36938, 9842), // trophy, medal2, winner, championship_belt
    "flag" to intArrayOf(178, 332), // finish_flag, flag_2
    "diamond" to intArrayOf(10172), // diamond_v1
    "alarm" to intArrayOf(2, 40401), // alarm_clock, alarm
    "timer" to intArrayOf(10082, 15929), // stopwatch, hourglass
    "calendar_month" to intArrayOf(226, 10062, 11272, 37876), // calendar, today, planner, tear_off_calendar
    "psychology" to intArrayOf(302), // brain
    "science" to intArrayOf(206, 19715), // test_tube, thin_test_tube
    "edit_note" to intArrayOf(5, 63, 108, 123479, 338), // ball_point_pen, pen, quill_with_ink, journal, hand_with_pen
    // People
    "person" to intArrayOf(197, 196, 60390), // user_male, user_female, person_female_v1
    "group" to intArrayOf(9999908, 32012), // couple, couple_man_woman
    "groups" to intArrayOf(94, 46204, 57), // user_group_man_man, friends, meeting
    "family_restroom" to intArrayOf(41, 9999949), // family_v1, family_of_three
    // dog, cat, dog_house, cat_footprint, dog_bone, german_shepherd
    "pets" to intArrayOf(40, 20, 9192, 9199, 9217, 44645),
    // baby, stroller, children, pacifier, baby_bottle, crying_baby
    "child_care" to intArrayOf(4, 76, 21, 8575, 8583, 8623),
    "elderly" to intArrayOf(19095, 8981, 8949, 9535), // elderly_person, old_man, old_woman, walking_stick
    "handshake" to intArrayOf(213, 16309), // handshake, agreement
    // Emotions
    "sentiment_very_satisfied" to intArrayOf(7868, 7852, 63376), // happy, lol, smiling
    "sentiment_neutral" to intArrayOf(61605, 39006, 59270), // neutral_emoticon_v1, so_so, bored
    "sentiment_sad" to intArrayOf(7850, 9728, 55462, 55463), // sad, crying, sad_female, sad_male
    "sentiment_worried" to intArrayOf(9999979, 9999988), // icons8_anxiety_250, afraid
    "sentiment_stressed" to intArrayOf(8958, 9999946), // under_pressure_male_v1, under_pressure_female
    "mood" to intArrayOf(7839), // cool
    // like, love, two_hearts, in_love, heart_with_arrow, filled_like
    "favorite" to intArrayOf(53, 11597, 10153, 7851, 31946, 37975),
    "volunteer_activism" to intArrayOf(7749, 360, 16001, 38074), // donate, volunteering, helping_hand, handshake_heart
    "spa" to intArrayOf(311, 8008, 37111, 23037, 101, 11517), // massage, spa, sauna, spa_candle, bath, jacuzzi
    // party_baloon, party_baloons, party_hat, party_hat_with_stars, confetti, firework, dancing_party
    "celebration" to intArrayOf(26142, 26143, 26197, 39542, 66917, 39296, 33902),
    "heart_broken" to intArrayOf(337), // broken_heart
)

private val ICON_BY_ID: Map<Int, String> =
    ICONS.flatMap { (key, ids) -> ids.map { it to key } }.toMap()

/**
 * Fallback for icons without a counterpart: keyword → Dakilio symbol key, checked in order; the first keyword
 * found in the label wins, so more specific keywords come first. Every key must exist in `DakilioSymbols`
 * (core:designsystem).
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

/**
 * A Dakilio icon for an imported Daylio activity: the counterpart of its Daylio [iconId] when there is one,
 * otherwise guessed from its English name.
 */
fun daylioTagIcon(label: String, iconId: Int = 0): String {
    ICON_BY_ID[iconId]?.let { return it }
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

/** Every key the tables can return, for tests. */
internal val daylioTagIconKeys: Set<String>
    get() = KEYWORDS.mapTo(mutableSetOf()) { it.second } + ICONS.map { it.first } + DEFAULT_TAG_ICON

/** Every Daylio icon id the table maps, with repeats, for tests. */
internal val daylioMappedIconIds: List<Int> get() = ICONS.flatMap { it.second.asList() }
