package com.santiago43rus.rupoop.network

import com.santiago43rus.rupoop.data.*
import retrofit2.http.*

interface RutubeApi {
    @GET("api/search/video/?format=json")
    suspend fun searchVideos(
        @Query("query") query: String,
        @Query("ordering") ordering: String? = null,
        @Query("page") page: Int = 1
    ): SearchResponse

    // Параметры — как у запроса сайта для ПК (client=wdp, mq=all). Без них в ответе нет 2160p.
    @GET("api/play/options/{id}/?format=json")
    suspend fun getVideoOptions(
        @Path("id") id: String,
        @Query("no_404") no404: String = "true",
        @Query("referer") referer: String = "https%3A%2F%2Frutube.ru",
        @Query("pver") pver: String = "v2",
        @Query("client") client: String = "wdp",
        @Query("mq") mq: String = "all",
        @Query("av1") av1: String = "1",
        @Query("ac_client") acClient: String = "web",
        @Query("ver") ver: String = "358.0.0",
        @Query("ad_ver") adVer: String = "v2"
    ): RutubeResponse

    @GET("api/video/perso" +
            "n/{id}/?format=json")
    suspend fun getAuthorVideos(
        @Path("id") id: String,
        @Query("ordering") ordering: String? = "-hits",
        @Query("page") page: Int = 1
    ): SearchResponse

    @GET("api/video/topic/1/?format=json")
    suspend fun getLiveStreams(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): SearchResponse
}

interface GistApi {
    @GET("gists")
    suspend fun listGists(
        @Header("Authorization") authHeader: String,
        @Query("per_page") perPage: Int = 100,
        @Query("page") page: Int = 1
    ): List<Gist>

    @GET("gists/{id}")
    suspend fun getGist(@Header("Authorization") authHeader: String, @Path("id") id: String): Gist

    @POST("gists")
    suspend fun createGist(@Header("Authorization") authHeader: String, @Body request: GistRequest): Gist

    @PATCH("gists/{id}")
    suspend fun updateGist(@Header("Authorization") authHeader: String, @Path("id") id: String, @Body request: GistRequest): Gist
}

interface GitHubApi {
    @GET("user")
    suspend fun getUser(@Header("Authorization") authHeader: String): GitHubUser
}

interface DeviceAuthApi {
    @POST("auth/device/code")
    suspend fun getDeviceCode(): DeviceCodeResponse

    @POST("auth/device/token")
    suspend fun getDeviceToken(@Body request: DeviceCodeRequest): DeviceTokenResponse
}

interface SuggestApi {
    @GET("complete/search?client=firefox&ds=yt")
    suspend fun getSuggestions(@Query("q") query: String): retrofit2.Response<okhttp3.ResponseBody>
}
