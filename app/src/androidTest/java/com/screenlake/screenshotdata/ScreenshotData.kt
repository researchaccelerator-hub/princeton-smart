package com.screenlake.screenshotdata

import android.os.Build
import androidx.annotation.RequiresApi
import com.screenlake.data.database.entity.ScreenshotEntity
import com.screenlake.data.database.entity.UserEntity
import com.screenlake.recorder.constants.ConstantSettings
import com.screenlake.recorder.services.ScreenshotService
import com.screenlake.recorder.utilities.TimeUtility

object ScreenshotData {
    //return a ScreenshotEntity data object with info about a screenshot
    fun saveScreenshotData(filename: String, currentAppInUse: String, sessionId:String, user: UserEntity) : ScreenshotEntity {
        val timestamp = TimeUtility.getCurrentTimestamp().toInstant()

        val screenshot = ScreenshotEntity()
        screenshot.user = user.username
        screenshot.epochTimeStamp = timestamp.epochSecond
        screenshot.timestamp = timestamp.toString()
        screenshot.sessionId = sessionId
        if(user.uploadImages) screenshot.imageUrl = "https://screenshot-img.s3.amazonaws.com${filename}"
        screenshot.currentAppInUse = currentAppInUse
        screenshot.currentAppRealNameInUse = ScreenshotService.appNameVsPackageName.getOrDefault(currentAppInUse, "")
        screenshot.localTimeStamp = TimeUtility.getCurrentTimestampDefaultTimezoneString()
        screenshot.isAppRestricted = ConstantSettings.RESTRICTED_APPS.contains(currentAppInUse)
        screenshot.filePath = filename
        screenshot.sessionDepth = ScreenshotService.lastUnlockTime?.let { timestamp.epochSecond.minus(it) }
        return screenshot
    }

    val screenshotList = listOf(
        ScreenshotEntity(
            user = "ted@gmail.com",
            imageUrl = null,
            timestamp = "2023-06-16T04:33:09.853Z",
            localTimeStamp = "Fri Jun 16 00:33:09 EDT 2023",
            currentAppInUse = "com.google.android.googlequicksearchbox",
            isAppRestricted = false,
            sessionId = "959b5cf3-25aa-46ee-9237-08ea563fc705",
            currentAppRealNameInUse = "Google",
            text = "\"1233 5 vm er 2x qo zl 84m\ndone\ncustomize sleep routine see all routines\ntype a message 4\n11 x\"",
            isOcrComplete = true,
            filePath = "/data/user/0/com.screenlake/files/img_9cc43536-beaf-42b1-8b8d-2a2e5cd73819_2023_06_16_24_33_09_853.jpg",
            sessionDepth = 2376
        ),
        ScreenshotEntity(
            user = "ted@gmail.com",
            imageUrl = null,
            timestamp = "2023-06-16T04:33:12.262Z",
            localTimeStamp = "Fri Jun 16 00:33:06 EDT 2023",
            currentAppInUse = "com.google.android.googlequicksearchbox",
            isAppRestricted = false,
            sessionId = "959b5cf3-25aa-46ee-9237-08ea563fc705",
            currentAppRealNameInUse = "Google",
            text = "\"1233 5 vm er 2x qo zl 84m\ndone\ncustomize sleep routine see all routines\ntype a message 4\n11 x\"",
            isOcrComplete = true,
            filePath = "/data/user/0/com.screenlake/files/img_782fd015-7b9d-4423-a35b-8e0ab393bf75_2023_06_16_24_33_06_261.jpg",
            sessionDepth = 2373
        ),
        ScreenshotEntity(
            user = "ted@gmail.com",
            imageUrl = null,
            timestamp = "2023-06-16T04:33:15.853Z",
            localTimeStamp = "Fri Jun 16 00:33:09 EDT 2023",
            currentAppInUse = "com.screenlake",
            isAppRestricted = false,
            sessionId = "959b5cf3-25aa-46ee-9237-08ea563fc705",
            currentAppRealNameInUse = "Google",
            text = "\"1233 5 vm er 2x qo zl 84m\ndone\ncustomize sleep routine see all routines\ntype a message 4\n11 x\"",
            isOcrComplete = true,
            filePath = "/data/user/0/com.screenlake/files/img_9cc43536-beaf-42b1-8b8d-2a2e5cd73819_2023_06_16_24_33_09_853.jpg",
            sessionDepth = 2376
        ),
        ScreenshotEntity(
            user = "ted@gmail.com",
            imageUrl = null,
            timestamp = "2023-06-16T04:33:18.262Z",
            localTimeStamp = "Fri Jun 16 00:33:06 EDT 2023",
            currentAppInUse = "com.screenlake",
            isAppRestricted = false,
            sessionId = "959b5cf3-25aa-46ee-9237-08ea563fc705",
            currentAppRealNameInUse = "Google",
            text = "\"1233 5 vm er 2x qo zl 84m\ndone\ncustomize sleep routine see all routines\ntype a message 4\n11 x\"",
            isOcrComplete = true,
            filePath = "/data/user/0/com.screenlake/files/img_782fd015-7b9d-4423-a35b-8e0ab393bf75_2023_06_16_24_33_06_261.jpg",
            sessionDepth = 2373
        )

    )
}