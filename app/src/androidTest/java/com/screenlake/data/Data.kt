package com.screenlake.data


import com.screenlake.data.database.entity.ScreenshotEntity
import com.screenlake.data.database.entity.SessionEntity
import com.screenlake.data.database.entity.UserEntity
import com.screenlake.recorder.services.util.ScreenshotData

object Data {
    val exampleUser = UserEntity(
        createdAt = "2023-09-08T10:00:00Z",
        email = "example@example.com",
        emailHash = "hash123",
        tenantId = "tenant_abc",
        tenantName = "Sample Tenant",
        updatedAt = "2023-09-08T14:30:00Z",
        username = "sample_user",
        createdTimestamp = "2023-09-08T10:00:00Z",
        panelId = "panel_456",
        panelName = "Sample Panel",
        _lastChangedAt = "2023-09-08T14:30:00Z",
        _version = "1",
        __typename = "UserType",
        sdk = "Android SDK",
        device = "Samsung Galaxy S21",
        model = "SM-G991U",
        product = "Samsung Product",
        uploadImages = true,
        isEmulator = false
    )

    val screenshotList = listOf(
        ScreenshotEntity(
            user = "tpsped@gmail.com",
            imageUrl = null,
            appSegmentId = "123",
            epochTimeStamp = 1631100000,
            timestamp = "2023-06-16T04:33:09.853Z",
            localTimeStamp = "Fri Jun 16 00:33:09 EDT 2023",
            currentAppInUse = "com.google.android.googlequicksearchbox",
            isAppRestricted = false,
            sessionId = "session_002",
            currentAppRealNameInUse = "Google",
            text = ScreenshotData.ocrCleanUp("\"1233 5 vm er 2x qo zl 84m\ndone\ncustomize sleep routine see all routines\ntype a message 4\n11 x\""),
            isOcrComplete = true,
            filePath = "/data/user/0/com.screenlake/files/img_9cc43536-beaf-42b1-8b8d-2a2e5cd73819_2023_06_16_24_33_09_853.jpg",
            sessionDepth = 2376
        ),
        ScreenshotEntity(
            user = "tpsped@gmail.com",
            imageUrl = null,
            appSegmentId = "123",
            epochTimeStamp = 1631100000,
            timestamp = "2023-06-16T04:33:06.262Z",
            localTimeStamp = "Fri Jun 16 00:33:06 EDT 2023",
            currentAppInUse = "com.google.android.googlequicksearchbox",
            isAppRestricted = false,
            sessionId = "session_003",
            currentAppRealNameInUse = "Google",
            text = ScreenshotData.ocrCleanUp("\"1233 5 vm er 2x qo zl 84m\ndone\ncustomize sleep routine see all routines\ntype a message 4\n11 x\""),
            isOcrComplete = true,
            filePath = "/data/user/0/com.screenlake/files/img_782fd015-7b9d-4423-a35b-8e0ab393bf75_2023_06_16_24_33_06_261.jpg",
            sessionDepth = 2373
        )
        // Add the remaining rows here...
    )

    val sessionList = listOf(
        SessionEntity(
            user = "alice_smith",
            sessionStartEpoch = 1631100000000, // September 8, 2023, 13:00 PM UTC
            sessionEndEpoch = 1631103600000,   // September 8, 2023, 14:00 PM UTC
            sessionStart = "2023-09-08 13:00:00",
            sessionEnd = "2023-09-08 14:00:00",
            sessionId = "session_002",
            secondsSinceLastActive = 600L, // Changed to Long
            sessionDuration = 3600, // 1 hour in seconds
            sessionCountPerDay = 2,
            fps = 30.5,
            panelId = "panel_456",
            tenantId = "tenant_abc"
        ),
        SessionEntity(
            user = "bob_jones",
            sessionStartEpoch = 1631110000000, // September 8, 2023, 15:00 PM UTC
            sessionEndEpoch = 1631113600000,   // September 8, 2023, 16:00 PM UTC
            sessionStart = "2023-09-08 15:00:00",
            sessionEnd = "2023-09-08 16:00:00",
            sessionId = "session_003",
            secondsSinceLastActive = 450L, // Changed to Long
            sessionDuration = 3600, // 1 hour in seconds
            sessionCountPerDay = 1,
            fps = 29.0,
            panelId = "panel_789",
            tenantId = "tenant_xyz"
        )
    )

    val nrcVadDictionaryString = """
        media 0.5 0.3 0.7
        social -0.2 0.8 -0.5
        word3 0.1 -0.2 0.6
        ...
    """.trimIndent()
}