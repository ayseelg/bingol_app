package com.example.bingol.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

data class ChatRequest(val question: String, val model: String)
data class ChatResponse(val answer: String)
data class ModelInfo(val name: String, val description: String, val status: String)

interface ChatApiService {
    @POST("ask-tumor")
    suspend fun sendQuestion(@Body request: ChatRequest): Response<ChatResponse>

    @GET("models")
    suspend fun getModels(): Response<Map<String, ModelInfo>>
}

object RetrofitClient {
    // Bilgisayar LAN IP’si (telefon ile aynı Wi-Fi ağı)
    private const val BASE_URL = "http://192.168.1.42:5000/"

    val chatApi: ChatApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ChatApiService::class.java)
    }
}
