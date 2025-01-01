package com.screenlake.data

import com.screenlake.data.database.entity.ScreenshotEntity
import com.screenlake.data.model.AppInfo
import com.screenlake.recorder.constants.ConstantSettings

import com.screenlake.recorder.services.util.ScreenshotData
import com.screenlake.recorder.utilities.TimeUtility
import java.lang.IllegalArgumentException
import java.time.Instant
import java.util.LinkedList
import java.util.Random
import java.util.UUID

class ScreenRecordServiceHelper {
    val chronologicalAppList = LinkedList<String>()
    var startTime: Instant? = null
    val newSessionEveryCount = 5
    var currentSessionCount = 0
    var currentSession = UUID.randomUUID().toString()

    init {
        startTime = Instant.now()
        chronologicalAppList.addAll(ConstantSettings.RESTRICTED_APPS)
    }

    private fun isNewSession(): Boolean {
        return currentSessionCount == newSessionEveryCount
    }

    private fun incrementAndCheckNewSession(): Boolean {
        currentSessionCount += 1

        return if (currentSessionCount == newSessionEveryCount){
            currentSessionCount = 0
            true
        }else {
            false
        }
    }

    private fun getNextText(): String {
        return generateRandomText(randomNumber())
    }

    private fun getNextApp(): String {
        return chronologicalAppList.poll()!!
    }

    private fun getNextTime(): Instant? {
        return startTime?.plusMillis(3000L)
    }

    private fun getFileName(): String {
        return "data/user/0/com.screenlake/files/img_${UUID.randomUUID()}_${TimeUtility.getFormattedScreenCaptureTime()}.jpg"
    }

    private fun getNextSessionId(): String {
        return if (incrementAndCheckNewSession()) {
            currentSession = UUID.randomUUID().toString()
            currentSession
        }else {
            currentSession
        }
    }

    fun build(size: Int) : List<ScreenshotEntity> {
        val result = mutableListOf<ScreenshotEntity>()
        if (size > chronologicalAppList.count()) {
            throw IllegalArgumentException("Size is bigger then list of apps.")
        }

        for (i in 0 until size) {
            val screenshot = ScreenshotData.saveScreenshotData(getFileName(), AppInfo(getNextApp(), ""), getNextSessionId(), Data.exampleUser)
            screenshot.text = getNextText()
            screenshot.epochTimeStamp = getNextTime()?.toEpochMilli()
            screenshot.timestamp = getNextTime().toString()
            result.add(screenshot)
        }

        return result
    }

    private fun generateRandomText(length: Int): String {
        val characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        val random = Random()
        val sb = StringBuilder(length)

        for (i in 0 until length) {
            val randomIndex = random.nextInt(characters.length)
            sb.append(characters[randomIndex])
        }

        return sb.toString()
    }

    private fun randomNumber(): Int {
        // Define the range (inclusive)
        val min = 10
        val max = 30

        // Generate a random number within the specified range
        return kotlin.random.Random.nextInt(min, max + 1)
    }
}