package com.sshautoforward.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ssh_keys",
)
data class SshKeyEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val privateKeyPath: String,
    val hasPassphrase: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)
