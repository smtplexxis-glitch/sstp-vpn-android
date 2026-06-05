package com.sstpvpn.android.model

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface VpnServerDao {
    @Query("SELECT * FROM vpn_servers ORDER BY isDefault DESC, createdAt DESC")
    fun getAllServers(): Flow<List<VpnServer>>
    @Query("SELECT * FROM vpn_servers WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefaultServer(): VpnServer?
    @Query("SELECT * FROM vpn_servers WHERE id = :id")
    suspend fun getServerById(id: Int): VpnServer?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertServer(server: VpnServer): Long
    @Update
    suspend fun updateServer(server: VpnServer)
    @Delete
    suspend fun deleteServer(server: VpnServer)
    @Query("UPDATE vpn_servers SET isDefault = 0")
    suspend fun clearAllDefaults()
    @Query("UPDATE vpn_servers SET isDefault = 1 WHERE id = :id")
    suspend fun setDefault(id: Int)
}

@Database(entities = [VpnServer::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun vpnServerDao(): VpnServerDao
    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "sstp_vpn_db").build().also { INSTANCE = it }
            }
    }
}
