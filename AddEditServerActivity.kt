package com.sstpvpn.android.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.sstpvpn.android.databinding.ActivityAddEditServerBinding
import com.sstpvpn.android.model.AppDatabase
import com.sstpvpn.android.model.VpnServer
import kotlinx.coroutines.launch

class AddEditServerActivity : AppCompatActivity() {
    companion object { const val EXTRA_SERVER_ID = "server_id" }
    private lateinit var binding: ActivityAddEditServerBinding
    private var editingServer: VpnServer? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddEditServerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        val serverId = intent.getIntExtra(EXTRA_SERVER_ID, -1)
        if (serverId != -1) { title = "Редактировать сервер"; loadServer(serverId) } else { title = "Добавить сервер" }
        binding.btnSave.setOnClickListener { saveServer() }
    }
    private fun loadServer(id: Int) {
        lifecycleScope.launch {
            val server = AppDatabase.getInstance(this@AddEditServerActivity).vpnServerDao().getServerById(id)
            server?.let { editingServer = it; binding.etName.setText(it.name); binding.etHost.setText(it.host); binding.etPort.setText(it.port.toString()); binding.etUsername.setText(it.username); binding.etPassword.setText(it.password); binding.switchDefault.isChecked = it.isDefault }
        }
    }
    private fun saveServer() {
        val name = binding.etName.text.toString().trim()
        val host = binding.etHost.text.toString().trim()
        val username = binding.etUsername.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()
        val isDefault = binding.switchDefault.isChecked
        if (name.isEmpty() || host.isEmpty() || username.isEmpty() || password.isEmpty()) { Toast.makeText(this, "Заполните все обязательные поля", Toast.LENGTH_SHORT).show(); return }
        val port = binding.etPort.text.toString().toIntOrNull() ?: 443
        lifecycleScope.launch {
            val db = AppDatabase.getInstance(this@AddEditServerActivity)
            if (isDefault) db.vpnServerDao().clearAllDefaults()
            val server = VpnServer(id = editingServer?.id ?: 0, name = name, host = host, port = port, username = username, password = password, isDefault = isDefault)
            if (editingServer != null) { db.vpnServerDao().updateServer(server); Toast.makeText(this@AddEditServerActivity, "Сервер обновлён", Toast.LENGTH_SHORT).show() }
            else { db.vpnServerDao().insertServer(server); Toast.makeText(this@AddEditServerActivity, "Сервер добавлен", Toast.LENGTH_SHORT).show() }
            finish()
        }
    }
    override fun onSupportNavigateUp(): Boolean { onBackPressedDispatcher.onBackPressed(); return true }
}
