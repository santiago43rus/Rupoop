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

    @GET("api/play/options/{id}/?format=json")
    suspend fun getVideoOptions(@Path("id") id: String): RutubeResponse

    @GET("api/video/person/{id}/?format=json")
    suspend fun getAuthorVideos(
        @Path("id") id: String,
        @Query("ordering") ordering: String? = "-created_ts",
        @Query("page") page: Int = 1
    ): SearchResponse
}

interface GistApi {
    @GET("gists")
    suspend fun listGists(@Header("Authorization") authHeader: String): List<Gist>

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
