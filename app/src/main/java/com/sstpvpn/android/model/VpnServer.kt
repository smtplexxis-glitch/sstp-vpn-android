package com.sstpvpn.android.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vpn_servers")
data class VpnServer(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val host: String,
    val port: Int = 443,
    val username: String,
    val password: String,
    val isDefault: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
