package com.bctags.bcstocks.ui.receives

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.AutoCompleteTextView
import android.widget.Toast
import com.bctags.bcstocks.R
import com.bctags.bcstocks.databinding.ActivityNewReceiveBinding
import com.bctags.bcstocks.io.ApiClient
import com.bctags.bcstocks.io.response.BranchData
import com.bctags.bcstocks.io.response.CarrierResponse
import com.bctags.bcstocks.io.response.PurchaseOrderData
import com.bctags.bcstocks.io.response.PurchaseOrderResponse
import com.bctags.bcstocks.io.response.SupplierData
import com.bctags.bcstocks.model.Filter
import com.bctags.bcstocks.model.FilterRequest
import com.bctags.bcstocks.model.Pagination
import com.bctags.bcstocks.model.ReceiveNew
import com.bctags.bcstocks.ui.DrawerBaseActivity
import com.bctags.bcstocks.util.DropDown
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response


class NewReceiveActivity : DrawerBaseActivity() {
    private lateinit var binding: ActivityNewReceiveBinding

    private val apiClient = ApiClient().apiService
    val dropDown = DropDown()

    val mapPurchaseOrders: HashMap<String, String> = HashMap()
    val mapCarriers: HashMap<String, String> = HashMap()

    var newReceive: ReceiveNew = ReceiveNew(0, 0, "",  mutableListOf())
    var purchaseOrder: PurchaseOrderData =  PurchaseOrderData(0,"",0,0,"","","", BranchData(0,""),mutableListOf(),SupplierData(0,""))

    val SERVER_ERROR="Server error, try later"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNewReceiveBinding.inflate(layoutInflater)
        setContentView(binding.root)
        getPurchaseOrderList()
        getCarrierList()
        initUI()
    }

    private fun initUI() {
        binding.ivGoBack.setOnClickListener{
            onBackPressedDispatcher.onBackPressed()
        }
        binding.btnScan.setOnClickListener {
            checkForm()
        }
    }

    private fun checkForm() {
        if(newReceive.carrierId!=0 && newReceive.purchaseOrderId!=0){
            scanActivity()
        }else{
            Toast.makeText(applicationContext, "Purchase order and Carried must be selected ",Toast.LENGTH_LONG).show()
        }
    }

    private fun scanActivity(){
        val intent = Intent(this, ScannerReceiveActivity::class.java)
        newReceive.comments= binding.etComments.text.toString()
//        intent.putExtra("purchaseOrderId",newReceive.purchaseOrderId)
//        intent.putExtra("carrierId",newReceive.carrierId)
//        intent.putExtra("comments", newReceive.comments)
        val gson = Gson()
        intent.putExtra("RECEIVE", gson.toJson(newReceive))
        intent.putExtra("PURCHASE_ORDER", gson.toJson(purchaseOrder))
        startActivity(intent)
    }

    private fun getCarrierList() {
        CoroutineScope(Dispatchers.IO).launch {
            val call = apiClient.getCarrierList()
            call.enqueue(object : Callback<CarrierResponse> {
                override fun onResponse(call: Call<CarrierResponse>,response: Response<CarrierResponse>) {
                    if (response.isSuccessful) {
                        val carrierResponse: CarrierResponse? = response.body()
                        var list: MutableList<String> = mutableListOf()
                        carrierResponse?.list?.forEach { i ->
                            list.add(i.name)
                            mapCarriers[i.name] = i.id.toString();
                        }
                        val autoComplete: AutoCompleteTextView = findViewById(R.id.carrierList)
                        dropDown.listArrange(list,autoComplete,mapCarriers,this@NewReceiveActivity,::updateCarriers)
                    } else {
                        Toast.makeText(applicationContext, SERVER_ERROR,Toast.LENGTH_SHORT).show()
                    }
                }
                override fun onFailure(call: Call<CarrierResponse>, t: Throwable) {
                    Toast.makeText(applicationContext,SERVER_ERROR,Toast.LENGTH_SHORT).show()
                }
            })
        }
    }

    private fun getPurchaseOrderList() {
        val pag = Pagination(1, 100)
        val poFilter = mutableListOf(Filter("status", "or", mutableListOf("sent", "in_process")))
        val poRequestBody = FilterRequest(poFilter, pag)

        CoroutineScope(Dispatchers.IO).launch {
            val call = apiClient.getPurchaseOrder(poRequestBody)
            call.enqueue(object : Callback<PurchaseOrderResponse> {
                override fun onResponse(call: Call<PurchaseOrderResponse>,response: Response<PurchaseOrderResponse>) {
                    if (response.isSuccessful) {
                        val purchaseOrderResponse: PurchaseOrderResponse? = response.body()
                        var list: MutableList<String> = mutableListOf()
                        purchaseOrderResponse?.list?.forEach { po ->
                            list.add(po.number)
                            mapPurchaseOrders[po.number] = po.id.toString();
                        }
                        val autoComplete: AutoCompleteTextView = findViewById(R.id.purchaseOrderList)
                        dropDown.listArrange(list,autoComplete,mapPurchaseOrders,this@NewReceiveActivity,::updatePo)
                    } else {
                        Toast.makeText(applicationContext, SERVER_ERROR,Toast.LENGTH_SHORT).show()
                    }
                }
                override fun onFailure(call: Call<PurchaseOrderResponse>, t: Throwable) {
                    Toast.makeText(applicationContext,SERVER_ERROR,Toast.LENGTH_SHORT).show()
                }
            })
        }
    }

    private fun updateCarriers(id: String, text: String){
        newReceive.carrierId=id.toInt()
    }

    private fun updatePo(id: String, text: String){
        newReceive.purchaseOrderId=id.toInt()
        searchPoSupplier(id)
    }

    private fun searchPoSupplier(id: String) {
        val pag = Pagination(1, 100)
        val poFilter = mutableListOf(Filter("id", "eq", mutableListOf(id)))
        val poRequestBody = FilterRequest(poFilter, pag)

        CoroutineScope(Dispatchers.IO).launch {
            val call = apiClient.getPurchaseOrder(poRequestBody)
            call.enqueue(object : Callback<PurchaseOrderResponse> {
                override fun onResponse(call: Call<PurchaseOrderResponse>,response: Response<PurchaseOrderResponse>) {
                    if (response.isSuccessful) {
                        val poResponse: PurchaseOrderResponse? = response.body()
                        if (poResponse != null) {
                            val text= "Supplier: " + poResponse.list[0].Supplier.name
                            binding.tvPoSupplier.text =text
                            purchaseOrder=poResponse.list[0]
                        }
                    } else {
                        Toast.makeText(applicationContext, SERVER_ERROR,Toast.LENGTH_SHORT).show()
                    }
                }
                override fun onFailure(call: Call<PurchaseOrderResponse>, t: Throwable) {
                    Toast.makeText(applicationContext,SERVER_ERROR,Toast.LENGTH_SHORT).show()
                }
            })
        }
    }


}