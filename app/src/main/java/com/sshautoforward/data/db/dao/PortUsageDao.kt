package com.sshautoforward.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sshautoforward.data.db.entity.PortUsageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PortUsageDao {
    @Query("SELECT * FROM port_usage WHERE hostId = :hostId")
    fun getByHostId(hostId: Long): Flow<List<PortUsageEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfMissing(usage: PortUsageEntity)

    @Query(
        "UPDATE port_usage SET clickCount = clickCount + 1, lastUsedAt = :now " +
            "WHERE hostId = :hostId AND remotePort = :remotePort"
    )
    suspend fun incrementClick(hostId: Long, remotePort: Int, now: Long)

    @Query(
        "UPDATE port_usage SET totalBytes = totalBytes + :bytes, lastUsedAt = :now " +
            "WHERE hostId = :hostId AND remotePort = :remotePort"
    )
    suspend fun addBytes(hostId: Long, remotePort: Int, bytes: Long, now: Long)
}
