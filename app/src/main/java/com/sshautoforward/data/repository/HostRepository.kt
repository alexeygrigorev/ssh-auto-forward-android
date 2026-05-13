package com.sshautoforward.data.repository

import com.sshautoforward.data.db.dao.HostDao
import com.sshautoforward.data.db.entity.HostEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HostRepository @Inject constructor(private val dao: HostDao) {
    fun getAll(): Flow<List<HostEntity>> = dao.getAll()

    fun getEnabled(): Flow<List<HostEntity>> = dao.getEnabled()

    suspend fun getById(id: Long): HostEntity? = dao.getById(id)

    suspend fun insert(host: HostEntity): Long = dao.insert(host)

    suspend fun update(host: HostEntity) = dao.update(host)

    suspend fun delete(host: HostEntity) = dao.delete(host)

    suspend fun deleteById(id: Long) = dao.deleteById(id)
}
