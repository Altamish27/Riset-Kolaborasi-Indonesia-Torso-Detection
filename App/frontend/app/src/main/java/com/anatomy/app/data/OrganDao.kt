package com.anatomy.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * OrganDao — Data Access Object for the 'organs' table.
 */
@Dao
interface OrganDao {

    @Query("SELECT * FROM organs WHERE name = :name LIMIT 1")
    suspend fun getOrganByName(name: String): OrganEntity?

    @Query("SELECT * FROM organs")
    suspend fun getAllOrgans(): List<OrganEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(organs: List<OrganEntity>)
    
    @Query("DELETE FROM organs")
    suspend fun deleteAll()
}
