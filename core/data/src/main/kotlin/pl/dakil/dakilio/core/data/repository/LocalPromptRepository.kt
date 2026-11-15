package pl.dakil.dakilio.core.data.repository

import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import pl.dakil.dakilio.core.database.dao.PromptDao
import pl.dakil.dakilio.core.database.entity.PromptEntity
import pl.dakil.dakilio.core.model.Prompt
import pl.dakil.dakilio.core.model.PromptDraft
import pl.dakil.dakilio.core.model.Prompts

internal class LocalPromptRepository @Inject constructor(
    private val dao: PromptDao,
    private val clock: Clock,
) : PromptRepository {
    override val prompts: Flow<List<Prompt>> = dao.observeAll().map { rows ->
        rows.map { Prompt(id = it.id, text = it.text, levels = Prompts.fromMask(it.levels), position = it.position) }
    }

    override val count: Flow<Int> = dao.observeCount()

    override suspend fun add(prompts: List<PromptDraft>): Int {
        val known = dao.observeAll().first().map { Prompts.key(it.text) }.toMutableSet()
        val now = clock.millis()
        var position = dao.nextPosition()
        val rows = prompts.mapNotNull { draft ->
            val text = Prompts.clean(draft.text)
            // A question that's already there (or twice in the list) is added once.
            if (text.isEmpty() || !known.add(Prompts.key(text))) return@mapNotNull null
            PromptEntity(text = text, levels = Prompts.toMask(draft.levels.ifEmpty { Prompts.ALL_LEVELS }), position = position++, createdAt = now)
        }
        dao.insertAll(rows)
        return rows.size
    }

    override suspend fun update(id: Long, draft: PromptDraft) {
        val text = Prompts.clean(draft.text)
        if (text.isNotEmpty()) dao.update(id, text, Prompts.toMask(draft.levels.ifEmpty { Prompts.ALL_LEVELS }))
    }

    override suspend fun delete(id: Long) = dao.delete(id)
}
