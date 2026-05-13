package com.sshautoforward.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.sshautoforward.data.db.dao.HostDao
import com.sshautoforward.data.db.dao.PortRemappingDao
import com.sshautoforward.data.db.dao.SshKeyDao
import com.sshautoforward.data.db.entity.HostEntity
import com.sshautoforward.data.db.entity.PortRemappingEntity
import com.sshautoforward.data.db.entity.SshKeyEntity

@Database(
    entities = [HostEntity::class, SshKeyEntity::class, PortRemappingEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun hostDao(): HostDao
    abstract fun sshKeyDao(): SshKeyDao
    abstract fun portRemappingDao(): PortRemappingDao
}
