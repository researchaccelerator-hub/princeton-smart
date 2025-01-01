package com.screenlake.data.database.dao

import androidx.room.*
import com.screenlake.data.database.entity.ScreenshotZipEntity

@Dao
interface ScreenshotZipDao {

    //database interaction, uses coroutines
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertZipObj(zipObj: ScreenshotZipEntity)

    @Delete
    suspend fun deleteZipObj(zipObj: ScreenshotZipEntity)

    @Delete
    fun deleteZipObjSync(zipObj: ScreenshotZipEntity)

    @Query("SELECT * FROM screenshot_zip_table")
    suspend fun getAllZipObjs(): MutableList<ScreenshotZipEntity>

    @Query("SELECT * FROM screenshot_zip_table")
    fun getAllZipObjsSynchronously(): MutableList<ScreenshotZipEntity>

    @Query("UPDATE screenshot_zip_table SET toDelete=:toDelete WHERE id = :id")
    suspend fun flagZipForDeletion(id: Int, toDelete: Boolean)

    @Query("DELETE FROM screenshot_zip_table WHERE id = :id")
    suspend fun delete(id: Int)

    @Query("DELETE FROM screenshot_zip_table WHERE id = :id")
    fun deleteSynchronous(id: Int)

    @Query("SELECT * FROM screenshot_zip_table")
    suspend fun getZipsToDelete(): List<ScreenshotZipEntity>

    @Query("SELECT Count(*) FROM screenshot_zip_table")
    suspend fun getZipCount(): Int

    @Query("SELECT * FROM screenshot_zip_table where toDelete is 0 limit 1")
    suspend fun getZipToUpload(): List<ScreenshotZipEntity>

    @Query("SELECT * FROM screenshot_zip_table where file is :fileName")
    suspend fun getScreenshotZipByFileName(fileName: String): ScreenshotZipEntity

    @Query("SELECT * FROM screenshot_zip_table where toDelete is 1 limit 1")
    suspend fun getZipToDeleteFlagged(): List<ScreenshotZipEntity>

    /**
     * Updating only price
     * By order id
     */
//    @Query("UPDATE screenshot_zip_table SET to_delete=:toDelete WHERE id = :id")
//    suspend fun update(toDelete: Boolean, id: Int)

    @Query("DELETE FROM screenshot_zip_table")
    suspend fun nukeTable()
    //
    @Query("SELECT COUNT(id) FROM screenshot_zip_table")
    suspend fun getCount(): Int
}