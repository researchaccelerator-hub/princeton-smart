package com.screenlake.data.database.entity

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.PrimaryKey

@Keep
@Entity(tableName = "word_seen_table")
data class TopicSeenIntervalEntity(
    val word: String,
    val synonymSeen: Boolean,
    val apk: String,
    val timestamp: Long,
    val sessionId: String? = null,
    var wordsAround: String? = null,
    var accessibilitySessionId: String? = null,
    var intervalId: String,
    var sentiment: String,
    var sentimentScore: Double,
){
    @PrimaryKey(autoGenerate = true)
    var id: Int? = null
}