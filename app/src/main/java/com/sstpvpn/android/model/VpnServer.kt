package com.sstpvpn.android.model

data class VpnServer(
    val id: Int = 0,
    val name: String,
    val host: String,
    val port: Int = 443,
    val username: String,
    val password: String,
    val isDefault: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
