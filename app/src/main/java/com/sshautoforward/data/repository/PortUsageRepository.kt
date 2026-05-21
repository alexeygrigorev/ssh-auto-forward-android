package com.sshautoforward.data.repository

import com.sshautoforward.data.db.dao.PortUsageDao
import com.sshautoforward.data.db.entity.PortUsageEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PortUsageRepository @Inject constructor(private val dao: PortUsageDao) {
    fun getByHostId(hostId: Long): Flow<List<PortUsageEntity>> = dao.getByHostId(hostId)

    suspend fun recordClick(hostId: Long, remotePort: Int) {
        val now = System.currentTimeMillis()
        dao.insertIfMissing(PortUsageEntity(hostId = hostId, remotePort = remotePort, lastUsedAt = now))
        dao.incrementClick(hostId, remotePort, now)
    }

    suspend fun addBytes(hostId: Long, remotePort: Int, bytes: Long) {
        if (bytes <= 0) return
        val now = System.currentTimeMillis()
        dao.insertIfMissing(PortUsageEntity(hostId = hostId, remotePort = remotePort, lastUsedAt = now))
        dao.addBytes(hostId, remotePort, bytes, now)
    }
}
