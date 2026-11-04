package pl.dakil.dakilio.core.data.repository

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import pl.dakil.dakilio.core.database.dao.KeywordDao
import pl.dakil.dakilio.core.database.dao.PostDao
import pl.dakil.dakilio.core.database.entity.TagKeywordEntity
import pl.dakil.dakilio.core.model.Keyword
import pl.dakil.dakilio.core.model.KeywordDraft
import pl.dakil.dakilio.core.model.Keywords

internal class LocalKeywordRepository @Inject constructor(
    private val dao: KeywordDao,
    private val postDao: PostDao,
) : KeywordRepository {
    override val keywords: Flow<List<Keyword>> = dao.observeAll().map { rows ->
        rows.map { Keyword(id = it.id, tagId = it.tagId, word = it.word, approximate = it.approximate) }
    }

    override suspend fun setKeywords(tagId: Long, keywords: List<KeywordDraft>) {
        val rows = keywords
            .map { it.copy(word = it.word.trim()) }
            .filter { Keywords.isUsable(it.word) }
            .distinctBy { Keywords.key(it.word) }
            .map { TagKeywordEntity(tagId = tagId, word = it.word, approximate = it.approximate, position = 0) }
        dao.replaceForTag(tagId, rows)
    }

    override suspend fun clear(tagId: Long) = dao.deleteForTag(tagId)

    override suspend fun addTagToPosts(tagId: Long, postIds: List<Long>) = postDao.addTag(tagId, postIds)
}
