package pl.dakil.dakilio.core.database

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Room's own change tracking, for callers that only need to know *that* tables changed. */
class TableChanges @Inject constructor(private val database: DakilioDatabase) {
    /** Emits once at the start and after every transaction that touched one of [tables]. */
    fun observe(vararg tables: String): Flow<Unit> = database.invalidationTracker.createFlow(*tables).map { }
}
