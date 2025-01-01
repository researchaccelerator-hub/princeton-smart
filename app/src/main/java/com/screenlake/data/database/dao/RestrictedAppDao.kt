package com.screenlake.data.database.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.screenlake.data.database.entity.RestrictedAppPersistentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RestrictedAppDao {

    /**
     * Inserts or updates a RestrictedAppPersistent.
     *
     * @param restrictedApp The RestrictedAppPersistent to be saved.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(restrictedApp: RestrictedAppPersistentEntity)

    /**
     * Inserts a RestrictedAppPersistent if it does not already exist.
     *
     * @param restrictedApp The RestrictedAppPersistent to be saved.
     */
    suspend fun insertRestrictedApp(restrictedApp: RestrictedAppPersistentEntity){
        val existingPackages = restrictedApp.packageName?.let { getRestrictedAppByPackageName(it).count() }
        val exists = existingPackages != null && existingPackages >= 1

        if(!exists) {
            insert(restrictedApp)
        }
    }

    /**
     * Updates the isUserRestricted field of a RestrictedAppPersistent.
     *
     * @param id The ID of the RestrictedAppPersistent to be updated.
     * @param isUserRestricted The new value for the isUserRestricted field.
     */
    @Query("UPDATE restricted_app_table SET isUserRestricted=:isUserRestricted WHERE id = :id")
    suspend fun updateRestrictedApp(id: Int, isUserRestricted: Boolean)

    /**
     * Deletes a RestrictedAppPersistent.
     *
     * @param restrictedApp The RestrictedAppPersistent to be deleted.
     */
    @Delete
    suspend fun deleteRestrictedApp(restrictedApp: RestrictedAppPersistentEntity)

    /**
     * Deletes RestrictedAppPersistents with the specified IDs.
     *
     * @param idList The list of IDs of the RestrictedAppPersistents to be deleted.
     */
    @Query("delete from restricted_app_table where id in (:idList)")
    suspend fun deleteRestrictedApps(idList: List<Int>)

    /**
     * Retrieves a list of RestrictedAppPersistents ordered by timestamp in descending order, limited by the specified number and offset.
     *
     * @param limit The maximum number of RestrictedAppPersistents to retrieve.
     * @param offset The offset from which to start retrieving RestrictedAppPersistents.
     * @return A list of RestrictedAppPersistents.
     */
    @Query("SELECT * FROM restricted_app_table ORDER BY timestamp DESC LIMIT :limit OFFSET :offset ")
    suspend fun getAllRestrictedAppsSortedByDate(limit: Int, offset: Int): List<RestrictedAppPersistentEntity>

    /**
     * Retrieves a list of RestrictedAppPersistents ordered by timestamp.
     *
     * @return A list of RestrictedAppPersistents.
     */
    @Query("SELECT * FROM restricted_app_table ORDER BY timestamp")
    suspend fun getAllRestrictedAppsSortedByDate(): List<RestrictedAppPersistentEntity>

    /**
     * Retrieves a flow of all RestrictedAppPersistents.
     *
     * @return A flow of RestrictedAppPersistents.
     */
    @Query("SELECT * FROM restricted_app_table")
    fun getAllRestrictedAppsFlow(): Flow<List<RestrictedAppPersistentEntity>>

    /**
     * Retrieves a list of all RestrictedAppPersistents.
     *
     * @return A list of RestrictedAppPersistents.
     */
    @Query("SELECT * FROM restricted_app_table")
    fun getAllRestrictedApps(): List<RestrictedAppPersistentEntity>

    /**
     * Retrieves a list of user-restricted RestrictedAppPersistents.
     *
     * @return A list of user-restricted RestrictedAppPersistents.
     */
    @Query("SELECT * FROM restricted_app_table where isUserRestricted is 1")
    fun getUserRestrictedApps(): List<RestrictedAppPersistentEntity>

    /**
     * Retrieves a list of RestrictedAppPersistents with the specified package name.
     *
     * @param packageName The package name to filter the RestrictedAppPersistents.
     * @return A list of RestrictedAppPersistents.
     */
    @Query("SELECT * FROM restricted_app_table Where packageName is :packageName")
    suspend fun getRestrictedAppByPackageName(packageName: String): List<RestrictedAppPersistentEntity>

    /**
     * Retrieves the count of RestrictedAppPersistents.
     *
     * @return The count of RestrictedAppPersistents.
     */
    @Query("SELECT COUNT(packageName) FROM restricted_app_table")
    fun getRestrictedAppCount(): LiveData<Int>

    /**
     * Deletes all RestrictedAppPersistents.
     */
    @Query("DELETE FROM restricted_app_table")
    suspend fun nukeTable()
}