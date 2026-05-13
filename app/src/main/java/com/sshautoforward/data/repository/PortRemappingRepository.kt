package com.sshautoforward.data.repository

import com.sshautoforward.data.db.dao.PortRemappingDao
import com.sshautoforward.data.db.entity.PortRemappingEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PortRemappingRepository @Inject constructor(private val dao: PortRemappingDao) {
    fun getByHostId(hostId: Long): Flow<List<PortRemappingEntity>> = dao.getByHostId(hostId)

    suspend fun getByRemotePort(hostId: Long, remotePort: Int): PortRemappingEntity? =
        dao.getByRemotePort(hostId, remotePort)

    suspend fun insert(remapping: PortRemappingEntity): Long = dao.insert(remapping)

    suspend fun deleteByRemotePort(hostId: Long, remotePort: Int) =
        dao.deleteByRemotePort(hostId, remotePort)

    suspend fun deleteByHostId(hostId: Long) = dao.deleteByHostId(hostId)
}
