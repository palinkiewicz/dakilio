package pl.dakil.dakilio.core.database

/**
 * The sample entries a fresh install is seeded with. Only debug and preview builds bind it (through
 * core:samples, which also ships their strings and photos), so release builds start empty.
 */
interface SampleData {
    val posts: List<DefaultData.SamplePost>
}
