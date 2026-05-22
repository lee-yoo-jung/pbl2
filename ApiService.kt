package com.example.pbl2

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

// 서버로 보낼 데이터 묶음 (이미지가 있으면 imageBase64에 값이 들어가고, 없으면 null)
data class QuestionRequest(
    val question: String,
    val mode: String,
    val imageBase64: String? = null,
    val packageName: String? = null,
    val language: String? = "한국어"
)

interface ApiService {

    // GET 방식에서 POST 방식으로 변경
    @POST("ask")
    suspend fun askQuestion(
        @Body request: QuestionRequest
    ): Response<AnswerResponse>

}