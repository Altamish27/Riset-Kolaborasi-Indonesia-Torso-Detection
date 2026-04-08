package com.anatomy.app.services

import android.content.Context
import android.util.Log
import com.anatomy.app.data.AnatomyDatabase
import com.anatomy.app.data.OrganEntity
import com.anatomy.app.network.ApiService
import com.anatomy.app.network.HttpClientFactory

/**
 * OrganService - Manages fetching organ data from API and storing in local database.
 *
 * This service is responsible for:
 * 1. Fetching organ list from the backend API
 * 2. Storing organs in Room database
 * 3. Providing organs from local database
 *
 * All organ data comes from the API - no hardcoded data.
 */
class OrganService(private val context: Context) {
    
    private val TAG = "OrganService"
    private val database = AnatomyDatabase.getInstance(context)
    private val organDao = database.organDao()
    private val apiService = HttpClientFactory.createApiService(context)
    
    /**
     * Sync organs from API to local database.
     * Call this once during app initialization or when you need fresh data from server.
     *
     * @return Success status: true if organs were fetched and stored, false otherwise
     */
    suspend fun syncOrgansFromAPI(): Boolean {
        return try {
            Log.d(TAG, "Starting organ sync from API...")
            
            // TODO: Replace with actual API endpoint from your backend
            // You need to create an endpoint that returns a list of organs
            // Expected response format:
            // {
            //   "organs": [
            //     {
            //       "name": "Jantung",
            //       "short_description": "...",
            //       "long_description": "..."
            //     },
            //     ...
            //   ]
            // }
            
            Log.w(TAG, "API endpoint for organ list not yet implemented. " +
                    "Please add OrganListResponse and update ApiService, then implement this method.")
            
            // Once the API is ready, the implementation should look like:
            // val response = apiService.getOrgans()  // You need to add this to ApiService
            // val organs = response.organs.map { it.toOrganEntity() }
            // organDao.insertAll(organs)
            
            false  // Return false until endpoint is ready
            
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing organs from API", e)
            false
        }
    }
    
    /**
     * Get all organs from local database
     */
    suspend fun getOrgansFromDB(): List<OrganEntity> {
        return try {
            organDao.getAllOrgans()
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching organs from database", e)
            emptyList()
        }
    }
    
    /**
     * Get a specific organ by name
     */
    suspend fun getOrganByName(name: String): OrganEntity? {
        return try {
            organDao.getOrganByName(name)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching organ: $name", e)
            null
        }
    }
    
    /**
     * Clear all organs from database
     */
    suspend fun clearOrgans() {
        try {
            organDao.deleteAll()
            Log.d(TAG, "All organs cleared from database")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing organs", e)
        }
    }
    
    /**
     * Insert organs manually (useful for testing or manual sync)
     */
    suspend fun insertOrgans(organs: List<OrganEntity>) {
        try {
            organDao.insertAll(organs)
            Log.d(TAG, "Inserted ${organs.size} organs into database")
        } catch (e: Exception) {
            Log.e(TAG, "Error inserting organs", e)
        }
    }
}

/**
 * TODO: Create these classes in network/ApiModels.kt once the backend API is finalized:
 * 
 * @kotlinx.serialization.Serializable
 * data class OrganListResponse(
 *     val organs: List<OrganDTO>
 * )
 * 
 * @kotlinx.serialization.Serializable
 * data class OrganDTO(
 *     val name: String,
 *     val short_description: String,
 *     val long_description: String
 * ) {
 *     fun toOrganEntity() = OrganEntity(
 *         name = name,
 *         short_description = short_description,
 *         long_description = long_description
 *     )
 * }
 * 
 * Then add this to ApiService interface:
 * 
 * @GET("/organs")  // Adjust endpoint path as needed
 * suspend fun getOrgans(): OrganListResponse
 */
