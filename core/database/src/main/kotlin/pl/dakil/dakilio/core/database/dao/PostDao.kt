package pl.dakil.dakilio.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import pl.dakil.dakilio.core.database.entity.PopulatedPost
import pl.dakil.dakilio.core.database.entity.PostEntity
import pl.dakil.dakilio.core.database.entity.PostPhotoEntity
import pl.dakil.dakilio.core.database.entity.PostTagCrossRef

@Dao
abstract class PostDao {
    /** Newest first. Relations are batch-loaded by Room (a handful of IN queries, not N+1). */
    @Transaction
    @Query("SELECT * FROM post ORDER BY timestamp DESC, id DESC")
    abstract fun observePosts(): Flow<List<PopulatedPost>>

    @Transaction
    @Query("SELECT * FROM post WHERE id = :id")
    abstract suspend fun getPost(id: Long): PopulatedPost?

    @Query("SELECT COUNT(*) FROM post WHERE id BETWEEN :fromId AND :toId")
    abstract fun observeCountInRange(fromId: Long, toId: Long): Flow<Int>

    @Query("SELECT file_name FROM post_photo WHERE post_id IN (:postIds)")
    abstract suspend fun photoFileNames(postIds: List<Long>): List<String>

    @Query("SELECT id FROM post WHERE id BETWEEN :fromId AND :toId")
    abstract suspend fun idsInRange(fromId: Long, toId: Long): List<Long>

    @Query("SELECT file_name FROM post_photo")
    abstract suspend fun allPhotoFileNames(): List<String>

    @Insert
    protected abstract suspend fun insert(post: PostEntity): Long

    @Update
    protected abstract suspend fun update(post: PostEntity)

    @Query("DELETE FROM post_tag WHERE post_id = :postId")
    protected abstract suspend fun clearTags(postId: Long)

    @Query("DELETE FROM post_photo WHERE post_id = :postId")
    protected abstract suspend fun clearPhotos(postId: Long)

    @Insert
    protected abstract suspend fun insertTags(refs: List<PostTagCrossRef>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertTagsIgnoring(refs: List<PostTagCrossRef>)

    /** Adds [tagId] to every entry in [postIds] that doesn't carry it yet. */
    @Transaction
    open suspend fun addTag(tagId: Long, postIds: List<Long>) {
        postIds.chunked(500).forEach { chunk -> insertTagsIgnoring(chunk.map { PostTagCrossRef(it, tagId) }) }
    }

    @Insert
    protected abstract suspend fun insertPhotos(photos: List<PostPhotoEntity>)

    @Query("DELETE FROM post WHERE id IN (:postIds)")
    abstract suspend fun delete(postIds: List<Long>)

    /** Inserts or fully replaces an entry and its relations atomically. Returns the entry id. */
    @Transaction
    open suspend fun save(
        post: PostEntity,
        tagIds: Collection<Long>,
        photoFileNames: List<String>,
    ): Long {
        val id = if (post.id == 0L) {
            insert(post)
        } else {
            update(post)
            clearTags(post.id)
            clearPhotos(post.id)
            post.id
        }
        insertTags(tagIds.map { PostTagCrossRef(id, it) })
        insertPhotos(photoFileNames.mapIndexed { index, name -> PostPhotoEntity(postId = id, fileName = name, position = index) })
        return id
    }
}
