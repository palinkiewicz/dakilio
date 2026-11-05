package pl.dakil.dakilio.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import pl.dakil.dakilio.core.database.entity.PhotoFileEntity

/** The sizes of stored photo files (`photo_file`), for the photo size setting. */
@Dao
interface PhotoDao {
    @Upsert
    suspend fun upsert(file: PhotoFileEntity)

    @Query("DELETE FROM photo_file WHERE file_name IN (:fileNames)")
    suspend fun delete(fileNames: Collection<String>)

    @Query("SELECT file_name FROM photo_file")
    suspend fun allFileNames(): List<String>

    /** How many photos entries use have a long edge over [maxEdge]. Only the two small tables are read. */
    @Query(
        """
        SELECT COUNT(DISTINCT f.file_name) FROM photo_file f
        JOIN post_photo p ON p.file_name = f.file_name
        WHERE MAX(f.width, f.height) > :maxEdge
        """,
    )
    fun observeCountLargerThan(maxEdge: Int): Flow<Int>

    /** The photos entries use with a long edge over [maxEdge]. */
    @Query(
        """
        SELECT DISTINCT f.* FROM photo_file f
        JOIN post_photo p ON p.file_name = f.file_name
        WHERE MAX(f.width, f.height) > :maxEdge
        """,
    )
    suspend fun largerThan(maxEdge: Int): List<PhotoFileEntity>

    /** Photos entries use whose size isn't known yet (e.g. sample photos seeded by plain SQL). */
    @Query("SELECT DISTINCT file_name FROM post_photo WHERE file_name NOT IN (SELECT file_name FROM photo_file)")
    suspend fun usedWithoutSize(): List<String>
}
