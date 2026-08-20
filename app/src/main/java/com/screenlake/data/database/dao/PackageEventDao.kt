package com.screenlake.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.screenlake.data.database.entity.PackageEventEntity

@Dao
interface PackageEventDao {

    /**
     * Inserts a PackageEvent.
     *
     * @param packageEvent The PackageEvent to be saved.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(packageEvent: PackageEventEntity)

    /**
     * Retrieves all PackageEvents ordered by eventTime in descending order, limited by the
     * specified number.
     *
     * @param limit The maximum number of PackageEvents to retrieve.
     * @return A list of PackageEvents.
     */
    @Query("SELECT * FROM package_event ORDER BY eventTime DESC LIMIT :limit")
    suspend fun getAllPackageEvents(limit: Int): List<PackageEventEntity>

    /**
     * Deletes PackageEvents with the specified IDs.
     *
     * @param idList The list of IDs of the PackageEvents to be deleted.
     */
    @Query("delete from package_event where id in (:idList)")
    suspend fun deletePackageEvents(idList: List<Int>)

    /**
     * Deletes all PackageEvents.
     */
    @Query("DELETE FROM package_event")
    suspend fun nukeTable()
}
