package com.example.gitsearch.data

import android.util.Log
import javax.inject.Inject

class GitHubRepository @Inject constructor(private val apiService: GitHubApiService) {

    suspend fun getUserRepositories(username: String): Result<List<Repository>> {
        return try {
            val response = apiService.getUserRepos(username)
            if (response.isSuccessful) {
                val repos = response.body() ?: emptyList()
                Result.success(repos)
            } else {
                if (response.code() == 404) {
                    Result.failure(Throwable("User not found"))
                } else {
                    Result.failure(Throwable("Error: ${response.message()}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(Throwable("Network error"))
        }
    }
    suspend fun searchUsers(query: String): Result<List<UserDto>> {
        return try {
            val response = apiService.searchUsers(query)
            Log.d("API Debug", "Response code: ${response.code()}, RateLimit-Remaining: ${response.headers()["X-RateLimit-Remaining"]}")

            if (response.isSuccessful) {
                val users = response.body()?.items ?: emptyList()
                Result.success(users)
            } else {
                val code = response.code()
                val rateLimitRemaining = response.headers()["X-RateLimit-Remaining"]?.toIntOrNull()

                if (code == 403 && rateLimitRemaining == 0) {
                    Result.failure(Throwable("GitHub API rate limit exceeded"))
                } else if (code == 404) {
                    Result.failure(Throwable("No users found"))
                } else {
                    Result.failure(Throwable("Error: ${response.message()}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(Throwable("Network error: ${e.localizedMessage}"))
        }
    }



}

