package com.anatomy.app

import android.app.Application
import android.util.Log
import com.anatomy.app.data.AnatomyDatabase
import com.anatomy.app.helper.AudioAssistant
import com.anatomy.app.helper.HapticHelper
import com.anatomy.app.services.OrganService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * AnatomyApp — Application subclass.
 *
 * Initializes global singletons and syncs organ data from API on app start.
 * - AudioAssistant: Text-to-speech and voice output
 * - HapticHelper: Haptic feedback (vibration)
 * - AnatomyDatabase: Local Room database (initially empty, populated from API)
 * - OrganService: Fetches organ data from backend API and stores in database
 */
class AnatomyApp : Application() {

    private val TAG = "AnatomyApp"
    lateinit var database: AnatomyDatabase
        private set
    
    private lateinit var organService: OrganService

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "AnatomyApp initializing...")
        
        try {
            AudioAssistant.init(this)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize AudioAssistant", e)
        }
        
        try {
            HapticHelper.init(this)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize HapticHelper", e)
        }
        
        try {
            database = AnatomyDatabase.getInstance(this)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Database", e)
            throw e
        }
        
        try {
            organService = OrganService(this)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize OrganService", e)
        }
        
        // Sync organs from API in background (non-blocking)
        CoroutineScope(Dispatchers.Default).launch {
            syncOrgansFromAPI()
        }
    }
    
    /**
     * Fetch organ data from API and cache in local database.
     * This runs in background and doesn't block app initialization.
     */
    private suspend fun syncOrgansFromAPI() {
        try {
            Log.d(TAG, "Syncing organs from API...")
            val success = organService.syncOrgansFromAPI()
            if (success) {
                Log.d(TAG, "Organ sync completed successfully")
            } else {
                Log.w(TAG, "Organ sync returned false - API endpoint may not be ready")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync organs from API", e)
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        AudioAssistant.shutdown()
        Log.d(TAG, "AnatomyApp terminated")
    }
}
