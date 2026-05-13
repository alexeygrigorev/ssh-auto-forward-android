package com.sshautoforward.data.repository

import com.sshautoforward.data.db.dao.SshKeyDao
import com.sshautoforward.data.db.entity.SshKeyEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SshKeyRepository @Inject constructor(private val dao: SshKeyDao) {
    fun getAll(): Flow<List<SshKeyEntity>> = dao.getAll()

    suspend fun getById(id: Long): SshKeyEntity? = dao.getById(id)

    suspend fun insert(key: SshKeyEntity): Long = dao.insert(key)

    suspend fun delete(key: SshKeyEntity) = dao.delete(key)

    suspend fun deleteById(id: Long) = dao.deleteById(id)
}
