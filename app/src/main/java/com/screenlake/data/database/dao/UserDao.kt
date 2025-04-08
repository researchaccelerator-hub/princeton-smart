package com.screenlake.data.database.dao

import androidx.room.*
import com.screenlake.data.database.entity.UserEntity

@Dao
interface UserDao {
    //database interaction, uses coroutines
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserObj(user: UserEntity)

    @Query("SELECT EXISTS (SELECT 1 FROM user_table)")
    suspend fun userExists(): Boolean

    @Query("SELECT * FROM user_table LIMIT 1")
    suspend fun getUser(): UserEntity

    @Query("SELECT * FROM user_table Where email_hash is not null LIMIT 1")
    fun getUserSynchronously(): UserEntity

    @Query("UPDATE user_table SET panel_id = :panelId WHERE id = :id")
    fun updatePanel(id:Int, panelId: String)

    /**
     * Deleting user
     */
    @Query("DELETE FROM user_table")
    suspend fun deleteUser()
}