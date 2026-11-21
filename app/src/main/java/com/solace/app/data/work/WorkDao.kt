package com.solace.app.data.work

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class WorkDao {

    @Transaction
    @Query("SELECT * FROM works WHERE status = :status ORDER BY updatedAt DESC")
    abstract fun observeByStatus(status: String): Flow<List<WorkWithMedia>>

    @Transaction
    @Query("SELECT * FROM works WHERE id = :id LIMIT 1")
    abstract suspend fun getById(id: String): WorkWithMedia?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertWork(work: WorkEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertMedia(items: List<WorkMediaEntity>)

    @Query("DELETE FROM work_media WHERE workId = :workId")
    abstract suspend fun deleteMediaFor(workId: String)

    @Query("DELETE FROM works WHERE id = :workId")
    abstract suspend fun deleteWork(workId: String)

    @Transaction
    open suspend fun save(work: WorkEntity, media: List<WorkMediaEntity>) {
        insertWork(work)
        deleteMediaFor(work.id)
        insertMedia(media)
    }

    @Transaction
    open suspend fun delete(workId: String) {
        deleteWork(workId)
    }
}
