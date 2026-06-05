package com.sstpvpn.android.utils

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class PrefsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("sstp_vpn_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()
    companion object {
        private const val KEY_SELECTED_APPS = "selected_apps"
        private const val KEY_SELECTED_SERVER_ID = "selected_server_id"
        private const val KEY_VPN_CONNECTED = "vpn_connected"
        @Volatile private var INSTANCE: PrefsManager? = null
        fun getInstance(context: Context): PrefsManager =
            INSTANCE ?: synchronized(this) { INSTANCE ?: PrefsManager(context.applicationContext).also { INSTANCE = it } }
    }
    var selectedApps: Set<String>
        get() { val json = prefs.getString(KEY_SELECTED_APPS, null) ?: return emptySet(); val type = object : TypeToken<Set<String>>() {}.type; return gson.fromJson(json, type) }
        set(value) { prefs.edit().putString(KEY_SELECTED_APPS, gson.toJson(value)).apply() }
    var selectedServerId: Int
        get() = prefs.getInt(KEY_SELECTED_SERVER_ID, -1)
        set(value) { prefs.edit().putInt(KEY_SELECTED_SERVER_ID, value).apply() }
    var isVpnConnected: Boolean
        get() = prefs.getBoolean(KEY_VPN_CONNECTED, false)
        set(value) { prefs.edit().putBoolean(KEY_VPN_CONNECTED, value).apply() }
}
