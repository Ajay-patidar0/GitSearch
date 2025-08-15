package com.example.gitsearch.data

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

data class Repository(
    @SerializedName("name") val name: String,
    @SerializedName("stargazers_count") val stars: Int,
    @SerializedName("language") val language: String?,
    @SerializedName("description") val description: String?,
    @SerializedName("html_url")val url: String
)


data class UserSearchResponseDto(
    @SerializedName("total_count")
    val totalCount: Int,

    @SerializedName("incomplete_results")
    val incompleteResults: Boolean,

    @SerializedName("items")
    val items: List<UserDto>
)
data class UserDto(
    @SerializedName("login") val login: String,
    @SerializedName("avatar_url") val avatarUrl: String
)


interface GitHubApiService {
    @GET("users/{username}/repos")
    suspend fun getUserRepos(@Path("username") username: String): Response<List<Repository>>


    @GET("search/users")
    suspend fun searchUsers(
        @Query("q")query: String,
        @Query("per_page") perPage: Int = 5
    ): Response<UserSearchResponseDto>
}
