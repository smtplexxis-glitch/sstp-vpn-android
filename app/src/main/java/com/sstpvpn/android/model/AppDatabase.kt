package com.sstpvpn.android.model

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class AppDatabase private constructor(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("vpn_servers_db", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val _serversFlow = MutableStateFlow<List<VpnServer>>(emptyList())

    init { _serversFlow.value = loadServers() }

    private fun loadServers(): List<VpnServer> {
        val json = prefs.getString("servers", null) ?: return emptyList()
        val type = object : TypeToken<List<VpnServer>>() {}.type
        return try { gson.fromJson(json, type) } catch (e: Exception) { emptyList() }
    }

    private fun saveServers(servers: List<VpnServer>) {
        prefs.edit().putString("servers", gson.toJson(servers)).apply()
        _serversFlow.value = servers
    }

    fun vpnServerDao() = object : VpnServerDao {
        override fun getAllServers(): Flow<List<VpnServer>> = _serversFlow.asStateFlow()
        override suspend fun getDefaultServer(): VpnServer? = loadServers().firstOrNull { it.isDefault }
        override suspend fun getServerById(id: Int): VpnServer? = loadServers().firstOrNull { it.id == id }
        override suspend fun insertServer(server: VpnServer): Long {
            val servers = loadServers().toMutableList()
            val maxId = servers.maxOfOrNull { it.id } ?: 0
            val newServer = server.copy(id = maxId + 1)
            servers.add(newServer); saveServers(servers); return newServer.id.toLong()
        }
        override suspend fun updateServer(server: VpnServer) {
            val servers = loadServers().map { if (it.id == server.id) server else it }
            saveServers(servers)
        }
        override suspend fun deleteServer(server: VpnServer) {
            saveServers(loadServers().filter { it.id != server.id })
        }
        override suspend fun clearAllDefaults() {
            saveServers(loadServers().map { it.copy(isDefault = false) })
        }
        override suspend fun setDefault(id: Int) {
            saveServers(loadServers().map { it.copy(isDefault = it.id == id) })
        }
    }

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppDatabase(context.applicationContext).also { INSTANCE = it }
            }
    }
}

interface VpnServerDao {
    fun getAllServers(): Flow<List<VpnServer>>
    suspend fun getDefaultServer(): VpnServer?
    suspend fun getServerById(id: Int): VpnServer?
    suspend fun insertServer(server: VpnServer): Long
    suspend fun updateServer(server: VpnServer)
    suspend fun deleteServer(server: VpnServer)
    suspend fun clearAllDefaults()
    suspend fun setDefault(id: Int)
}
