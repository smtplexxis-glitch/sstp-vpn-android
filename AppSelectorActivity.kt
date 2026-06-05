package com.sstpvpn.android.ui
import android.content.pm.*
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.*
import com.sstpvpn.android.R
import com.sstpvpn.android.databinding.ActivityAppSelectorBinding
import com.sstpvpn.android.model.AppInfo
import com.sstpvpn.android.utils.PrefsManager
class AppSelectorActivity : AppCompatActivity() {
  private lateinit var binding: ActivityAppSelectorBinding
  private lateinit var adapter: AppAdapter
  private lateinit var prefs: PrefsManager
  private var allApps = listOf<AppInfo>()
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState); binding = ActivityAppSelectorBinding.inflate(layoutInflater); setContentView(binding.root)
    setSupportActionBar(binding.toolbar); supportActionBar?.setDisplayHomeAsUpEnabled(true); title = "Выбор приложений"
    prefs = PrefsManager.getInstance(this)
    adapter = AppAdapter { app, selected -> val cur = prefs.selectedApps.toMutableSet(); if(selected) cur.add(app.packageName) else cur.remove(app.packageName); prefs.selectedApps = cur; updateSubtitle() }
    binding.recyclerView.layoutManager = LinearLayoutManager(this); binding.recyclerView.adapter = adapter
    binding.btnAllTraffic.setOnClickListener { prefs.selectedApps = emptySet(); loadApps(); updateSubtitle() }
    loadApps(); updateSubtitle()
  }
  private fun loadApps() {
    binding.progressBar.visibility = View.VISIBLE
    val sel = prefs.selectedApps; val pm = packageManager
    allApps = pm.getInstalledApplications(PackageManager.GET_META_DATA).filter { (it.flags and ApplicationInfo.FLAG_SYSTEM)==0 || (it.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)!=0 }.map { AppInfo(it.packageName, pm.getApplicationLabel(it).toString(), it.packageName in sel) }.sortedWith(compareByDescending<AppInfo>{it.isSelected}.thenBy{it.appName})
    adapter.submitList(allApps); binding.progressBar.visibility = View.GONE
  }
  private fun updateSubtitle() { val n=prefs.selectedApps.size; supportActionBar?.subtitle = if(n==0) "Весь трафик через VPN" else "Выбрано: $n" }
  override fun onCreateOptionsMenu(menu: Menu): Boolean {
    menuInflater.inflate(R.menu.app_selector_menu, menu)
    val sv = menu.findItem(R.id.action_search).actionView as SearchView
    sv.setOnQueryTextListener(object:SearchView.OnQueryTextListener{override fun onQueryTextSubmit(q:String?)=false;override fun onQueryTextChange(t:String?):Boolean{adapter.submitList(allApps.filter{it.appName.contains(t?:"",true)});return true}})
    return true
  }
  override fun onSupportNavigateUp(): Boolean { onBackPressedDispatcher.onBackPressed(); return true }
}
class AppAdapter(private val onToggle:(AppInfo,Boolean)->Unit):RecyclerView.Adapter<AppAdapter.ViewHolder>(){
  private var apps=listOf<AppInfo>()
  fun submitList(list:List<AppInfo>){apps=list;notifyDataSetChanged()}
  override fun onCreateViewHolder(p:ViewGroup,v:Int)=ViewHolder(LayoutInflater.from(p.context).inflate(R.layout.item_app,p,false))
  override fun onBindViewHolder(h:ViewHolder,i:Int)=h.bind(apps[i])
  override fun getItemCount()=apps.size
  inner class ViewHolder(view:View):RecyclerView.ViewHolder(view){
    private val icon:ImageView=view.findViewById(R.id.ivAppIcon)
    private val tvName:TextView=view.findViewById(R.id.tvAppName)
    private val tvPkg:TextView=view.findViewById(R.id.tvPackageName)
    private val cb:CheckBox=view.findViewById(R.id.checkBox)
    fun bind(app:AppInfo){tvName.text=app.appName;tvPkg.text=app.packageName;cb.isChecked=app.isSelected;try{icon.setImageDrawable(itemView.context.packageManager.getApplicationIcon(app.packageName))}catch(_:Exception){icon.setImageResource(R.drawable.ic_app_default)};itemView.setOnClickListener{val ns=!cb.isChecked;cb.isChecked=ns;onToggle(app,ns)};cb.setOnCheckedChangeListener{_,c->onToggle(app,c)}}
  }
}
