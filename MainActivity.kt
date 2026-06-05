package com.sstpvpn.android.ui
import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.sstpvpn.android.R
import com.sstpvpn.android.databinding.ActivityMainBinding
import com.sstpvpn.android.model.*
import com.sstpvpn.android.utils.PrefsManager
import com.sstpvpn.android.vpn.SstpVpnService
import kotlinx.coroutines.launch
class MainActivity : AppCompatActivity() {
  private lateinit var binding: ActivityMainBinding
  private lateinit var prefs: PrefsManager
  private var currentServer: VpnServer? = null
  private val vpnPermLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result -> if (result.resultCode == Activity.RESULT_OK) startVpnService() else Toast.makeText(this, "VPN разрешение отклонено", Toast.LENGTH_SHORT).show() }
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState); binding = ActivityMainBinding.inflate(layoutInflater); setContentView(binding.root); setSupportActionBar(binding.toolbar)
    prefs = PrefsManager.getInstance(this)
    binding.btnConnect.setOnClickListener { if (SstpVpnService.isRunning) disconnectVpn() else connectVpn() }
    binding.btnSelectApps.setOnClickListener { startActivity(Intent(this, AppSelectorActivity::class.java)) }
    binding.btnManageServers.setOnClickListener { startActivity(Intent(this, ServerListActivity::class.java)) }
    SstpVpnService.statusCallback = { status -> runOnUiThread { binding.tvStatus.text = status } }
    updateButtonState(SstpVpnService.isRunning)
    loadCurrentServer()
  }
  private fun loadCurrentServer() {
    lifecycleScope.launch {
      val db = AppDatabase.getInstance(this@MainActivity)
      val sid = prefs.selectedServerId
      currentServer = if (sid != -1) db.vpnServerDao().getServerById(sid) else db.vpnServerDao().getDefaultServer()
      if (currentServer == null) {
        val def = VpnServer(name="Германия (по умолчанию)", host="64.188.69.242", port=443, username="tel", password="H78fgk159s", isDefault=true)
        val id = db.vpnServerDao().insertServer(def); currentServer = def.copy(id=id.toInt())
      }
      runOnUiThread { currentServer?.let { binding.tvServerName.text=it.name; binding.tvServerHost.text="${it.host}:${it.port}" } }
    }
  }
  private fun connectVpn() { val i=VpnService.prepare(this); if(i!=null) vpnPermLauncher.launch(i) else startVpnService() }
  private fun startVpnService() { val i=Intent(this,SstpVpnService::class.java).apply{action=SstpVpnService.ACTION_CONNECT;currentServer?.let{putExtra(SstpVpnService.EXTRA_SERVER_ID,it.id)}}; ContextCompat.startForegroundService(this,i); updateButtonState(true); binding.tvStatus.text="Подключение..." }
  private fun disconnectVpn() { startService(Intent(this,SstpVpnService::class.java).apply{action=SstpVpnService.ACTION_DISCONNECT}); updateButtonState(false); binding.tvStatus.text="Отключено" }
  private fun updateButtonState(connected:Boolean) {
    if(connected){binding.btnConnect.text="Отключить VPN";binding.btnConnect.setBackgroundColor(getColor(R.color.red_disconnect));binding.statusIndicator.setBackgroundResource(R.drawable.circle_green)}
    else{binding.btnConnect.text="Подключить VPN";binding.btnConnect.setBackgroundColor(getColor(R.color.green_connect));binding.statusIndicator.setBackgroundResource(R.drawable.circle_red)}
    val n=prefs.selectedApps.size; binding.tvSelectedApps.text=if(n==0)"Весь трафик через VPN" else "Выбрано приложений: $n"
  }
  override fun onResume() { super.onResume(); updateButtonState(SstpVpnService.isRunning); loadCurrentServer() }
  override fun onCreateOptionsMenu(menu:Menu):Boolean { menuInflater.inflate(R.menu.main_menu,menu); return true }
  override fun onOptionsItemSelected(item:MenuItem):Boolean = when(item.itemId){R.id.action_servers->{startActivity(Intent(this,ServerListActivity::class.java));true};else->super.onOptionsItemSelected(item)}
  override fun onDestroy() { SstpVpnService.statusCallback=null; super.onDestroy() }
}
