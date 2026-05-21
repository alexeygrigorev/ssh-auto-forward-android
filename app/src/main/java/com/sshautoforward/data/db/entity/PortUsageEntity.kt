package com.sshautoforward.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "port_usage",
    primaryKeys = ["hostId", "remotePort"],
    foreignKeys = [
        ForeignKey(
            entity = HostEntity::class,
            parentColumns = ["id"],
            childColumns = ["hostId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("hostId")],
)
data class PortUsageEntity(
    val hostId: Long,
    val remotePort: Int,
    val clickCount: Int = 0,
    val totalBytes: Long = 0,
    val lastUsedAt: Long = 0,
)
