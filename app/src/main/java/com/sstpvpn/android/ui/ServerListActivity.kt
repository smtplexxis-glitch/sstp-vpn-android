package com.sstpvpn.android.ui
import android.content.Intent
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.*
import com.sstpvpn.android.R
import com.sstpvpn.android.databinding.ActivityServerListBinding
import com.sstpvpn.android.model.*
import com.sstpvpn.android.utils.PrefsManager
import kotlinx.coroutines.launch
class ServerListActivity : AppCompatActivity() {
  private lateinit var binding: ActivityServerListBinding
  private lateinit var adapter: ServerAdapter
  private lateinit var prefs: PrefsManager
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState); binding = ActivityServerListBinding.inflate(layoutInflater); setContentView(binding.root)
    setSupportActionBar(binding.toolbar); supportActionBar?.setDisplayHomeAsUpEnabled(true); title = "Серверы VPN"
    prefs = PrefsManager.getInstance(this)
    adapter = ServerAdapter(
      onSelect = { server -> lifecycleScope.launch { val db = com.sstpvpn.android.model.AppDatabase.getInstance(this@ServerListActivity); db.vpnServerDao().clearAllDefaults(); db.vpnServerDao().setDefault(server.id); prefs.selectedServerId = server.id } },
      onEdit = { server -> val intent = Intent(this, AddEditServerActivity::class.java); intent.putExtra(AddEditServerActivity.EXTRA_SERVER_ID, server.id); startActivity(intent) },
      onDelete = { server -> AlertDialog.Builder(this).setTitle("Удалить сервер?").setMessage("Удалить \"${server.name}\"?").setPositiveButton("Удалить") { _, _ -> lifecycleScope.launch { com.sstpvpn.android.model.AppDatabase.getInstance(this@ServerListActivity).vpnServerDao().deleteServer(server) } }.setNegativeButton("Отмена", null).show() }
    )
    binding.recyclerView.layoutManager = LinearLayoutManager(this); binding.recyclerView.adapter = adapter
    binding.fab.setOnClickListener { startActivity(Intent(this, AddEditServerActivity::class.java)) }
    lifecycleScope.launch { com.sstpvpn.android.model.AppDatabase.getInstance(this@ServerListActivity).vpnServerDao().getAllServers().collect { servers -> adapter.submitList(servers); binding.tvEmpty.visibility = if (servers.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE } }
  }
  override fun onSupportNavigateUp(): Boolean { onBackPressedDispatcher.onBackPressed(); return true }
}
class ServerAdapter(private val onSelect:(VpnServer)->Unit, private val onEdit:(VpnServer)->Unit, private val onDelete:(VpnServer)->Unit) : RecyclerView.Adapter<ServerAdapter.ViewHolder>() {
  private var servers = listOf<VpnServer>()
  fun submitList(list:List<VpnServer>){servers=list;notifyDataSetChanged()}
  override fun onCreateViewHolder(parent:ViewGroup,viewType:Int):ViewHolder = ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_server,parent,false))
  override fun onBindViewHolder(holder:ViewHolder,position:Int) = holder.bind(servers[position])
  override fun getItemCount() = servers.size
  inner class ViewHolder(view:android.view.View):RecyclerView.ViewHolder(view){
    private val radio:RadioButton=view.findViewById(R.id.radioDefault)
    private val tvName:TextView=view.findViewById(R.id.tvServerName)
    private val tvHost:TextView=view.findViewById(R.id.tvServerHost)
    private val btnEdit:ImageButton=view.findViewById(R.id.btnEdit)
    private val btnDelete:ImageButton=view.findViewById(R.id.btnDelete)
    fun bind(server:VpnServer){tvName.text=server.name;tvHost.text="${server.host}:${server.port}";radio.isChecked=server.isDefault;radio.setOnClickListener{onSelect(server)};itemView.setOnClickListener{onSelect(server)};btnEdit.setOnClickListener{onEdit(server)};btnDelete.setOnClickListener{onDelete(server)}}
  }
}
