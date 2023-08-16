package com.bctags.bcstocks.io

import com.bctags.bcstocks.io.response.CarrierResponse
import com.bctags.bcstocks.io.response.LoginResponse
import com.bctags.bcstocks.io.response.PurchaseOrderResponse
import com.bctags.bcstocks.io.response.UserResponse
import com.bctags.bcstocks.model.FilterRequest
import com.bctags.bcstocks.model.FilterRequestOnlyPag
import com.bctags.bcstocks.model.LoginRequest
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.GET


interface ApiService {

    @POST(value = "/auth/signin")//
    fun login(@Body requestBody: LoginRequest): Call<LoginResponse>

    @POST(value = "/auth/user")
    fun getCurrentUser(): Call<UserResponse>

    @POST(value = "/po/list")
    fun getPurchaseOrder(@Body requestBody: FilterRequest): Call<PurchaseOrderResponse>

    @POST(value = "/carrier/list")
    fun getCarrierList(): Call<CarrierResponse>



}