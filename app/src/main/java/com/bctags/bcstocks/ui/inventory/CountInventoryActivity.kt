package com.bctags.bcstocks.ui.inventory

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bctags.bcstocks.R
import com.bctags.bcstocks.databinding.ActivityCountInventoryBinding
import com.bctags.bcstocks.io.ApiCall
import com.bctags.bcstocks.io.ApiClient
import com.bctags.bcstocks.io.response.InventoryCount
import com.bctags.bcstocks.io.response.InventoryData
import com.bctags.bcstocks.model.CountLocation
import com.bctags.bcstocks.model.Filter
import com.bctags.bcstocks.model.FilterRequest
import com.bctags.bcstocks.model.Pagination
import com.bctags.bcstocks.ui.MainMenuActivity
import com.bctags.bcstocks.ui.inventory.adapterInventoryCount.CounterAdapter
import com.bctags.bcstocks.ui.transfer.TransferActivity
import com.bctags.bcstocks.util.DrawerBaseActivity
import com.bctags.bcstocks.util.EPCTools
import com.bctags.bcstocks.util.MessageDialog
import com.bumptech.glide.Glide
import com.google.gson.Gson
import com.rscja.deviceapi.RFIDWithUHFUART
import com.rscja.deviceapi.entity.UHFTAGInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.withContext

class CountInventoryActivity : DrawerBaseActivity() {
    private lateinit var binding: ActivityCountInventoryBinding
    private lateinit var adapter: CounterAdapter
    private val messageDialog = MessageDialog()
    private val apiClient = ApiClient().apiService
    private val apiCall = ApiCall()
    val gson = Gson()
    val tools = EPCTools()
    var selectedLocations: CountLocation = CountLocation(0, "")

    val DURACION: Long = 2500;
    private var isScanning = true
    private var triggerEnabled = true
    var rfid: RFIDWithUHFUART = RFIDWithUHFUART.getInstance()
    val epcsList: MutableList<String> = mutableListOf()
    var hashUpcs: MutableMap<String, Int> = mutableMapOf()
    private var filters: MutableList<Filter> = mutableListOf()
    val SERVER_ERROR = "Server error, try later"

    private var listInventory: MutableList<InventoryCount> = mutableListOf()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCountInventoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val extras = intent.extras
        if (extras != null) {
//            val selectedLocationsType = object : TypeToken<MutableList<CountLocation>>() {}.type
//            selectedLocations =  gson.fromJson(intent.getStringExtra("LOCATIONS_SELECTED"), selectedLocationsType)
            selectedLocations = gson.fromJson(
                intent.getStringExtra("LOCATIONS_SELECTED"),
                CountLocation::class.java
            )
            Log.i("location", selectedLocations.id.toString())
            getInventory()
        }
        lockScanButton()
        scannerGif()
        initListeners()
        readTag()

    }
    private fun lockScanButton(){
        binding.tvScan.isEnabled = false
        triggerEnabled=false
        Handler(Looper.getMainLooper()).postDelayed({
            binding.tvScan.isEnabled = true
            triggerEnabled=true
        }, 1000)
    }
    //private var rfidContext = newSingleThreadContext("RFIDThread")
    private fun readTag() {
        lifecycleScope.launch(newSingleThreadContext("readTagCountInventory")) {
            withContext(Dispatchers.IO) {
                val result: Boolean = rfid.init();
                if (!result) {
                    Log.i("DIDN'T WORK", "DIDN'T WORK")
                    rfid.stopInventory()
                    rfid.free()
                } else {
                    if (rfid.startInventoryTag()) {
                        Log.i("WORKS", "WORKS")
                        isScanning = true
                        tagsReader()
                    } else {
                        stopInventory()
                    }
                }
            }
        }
    }

    private fun tagsReader() {
        lifecycleScope.launch(newSingleThreadContext("tagsReaderCountInventory")) {
            while (isScanning) {
                val uhftagInfo: UHFTAGInfo? = rfid.readTagFromBuffer()
                if (uhftagInfo != null) {
                    epcsList.add(uhftagInfo.epc.toString())
                    Log.i("EPC", uhftagInfo.epc.toString())
                }
            }
        }
    }

    private fun stopInventory() {
        lifecycleScope.launch {
            isScanning = false
            rfid.stopInventory()
            rfid.free()
            val btnText = "Scan"
            //withContext(Dispatchers.Main) {
            binding.tvScan.text = btnText
            lockScanButton()
            if (epcsList.isNotEmpty()) {
                val list = epcsList.distinct() as MutableList<String>
                Log.i("epcsList", list.toString())
                countUpcs(list)
            } else {
                messageDialog.showDialog(
                    this@CountInventoryActivity,
                    R.layout.dialog_error,
                    "An error occurred.\nTry again."
                ) { }
            }
        }
        //}
    }

    private fun countUpcs(epcsList: MutableList<String>) {
        //USABA UN HILO PROPIO, SOLO EL HILO PRINCIPAL PUEDE MODIFICAR LA UI, POR ESO TRONABA
        lifecycleScope.launch {
            epcsList.forEach { i ->
                try{
                    val upc = tools.getGTIN(i).toString()
                    hashUpcs[upc] = (hashUpcs[upc] ?: 0) + 1
//                    if (hashUpcs.isEmpty() || !hashUpcs.containsKey(upc)) {
//                        hashUpcs[upc] = 1
//                    } else {
//                        hashUpcs[upc] = hashUpcs[upc]!! + 1
//                    }
                }catch (e: Exception) {
                    Log.e("Error", "Error Scanner: ${e.message}")
                }
            }
            checkReceivesUpcs()
        }
    }

    private fun checkReceivesUpcs() {
        Log.i("hashUpcs", hashUpcs.toString())
        if(listInventory.isNotEmpty()) {
            listInventory.forEach {
                it.founded = hashUpcs.getOrDefault(it.Item.upc, 0).toInt()
//            if(hashUpcs.isNotEmpty()) {
//                if (hashUpcs.containsKey(it.Item.upc)) {
//                    it.founded = hashUpcs[it.Item.upc]?.toInt() ?: 0
//                }
//            }
            }
            initRecyclerView()
        }
    }

    private fun initRecyclerView() {
        adapter = CounterAdapter(
            list = listInventory,
        )
        binding.recyclerList.layoutManager = LinearLayoutManager(this)
        binding.recyclerList.adapter = adapter
    }


    private fun getInventory() {
        val pag = Pagination(1, 10000)
        filters.add(Filter("locationId", "eq", mutableListOf(selectedLocations.id.toString())))
        CoroutineScope(Dispatchers.IO).launch {
            apiCall.performApiCall(
                apiClient.getInventory(FilterRequest(filters, pag)),
                onSuccess = { response ->
                    initInventory(response.data)
                },
                onError = { error ->
                    Log.i("error", gson.toJson(error))
                    Toast.makeText(applicationContext, SERVER_ERROR, Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    private fun initInventory(data: MutableList<InventoryData>) {
        listInventory.addAll(data.map { i -> InventoryCount(i.id, i.quantity, 0, i.Item, i.Location) })
//        if(data.isNotEmpty()) {
//            data.forEach { i ->
//                listInventory.add(InventoryCount(i.id, i.quantity, 0, i.Item, i.Location))
//            }
//        }
    }

    private fun scannerGif() {
        val logo = findViewById<ImageView>(R.id.ivScanning)
        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        Glide.with(this).asGif().load(R.drawable.scan_gif).into(logo)
        @Suppress("DEPRECATION")
        Handler().postDelayed(Runnable {
            binding.cvScanning.visibility = View.GONE;
        }, DURACION)
    }

    private fun initListeners() {
        binding.tvScan.setOnClickListener {
            initRead()
        }
        binding.llHeader.setOnClickListener {
            //onBackPressedDispatcher.onBackPressed()
            goMenu()
        }
    }

    private fun goMenu() {
        val intent = Intent(this, MainMenuActivity::class.java)
        startActivity(intent)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == 294 && triggerEnabled) {
            initRead()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun initRead() {
        lifecycleScope.launch {
            if (isScanning) {
                stopInventory()
            } else {
                val btnText = "Stop"
                binding.tvScan.text = btnText
                lockScanButton()
                isScanning = true
                listInventory.clear()
                hashUpcs.clear()
                getInventory()
                initRecyclerView()
                readTag()
            }
        }
    }


}

