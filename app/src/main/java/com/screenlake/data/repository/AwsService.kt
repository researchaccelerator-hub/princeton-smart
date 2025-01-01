package com.screenlake.data.repository

import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.PUT
import retrofit2.http.Url

interface AwsService {

    @PUT
    fun uploadAsset(
        @Url uploadUrl: String,
        @Body file: RequestBody
    ): Call<ResponseBody>

}