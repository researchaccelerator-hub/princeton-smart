package com.screenlake.recorder.utilities

import android.annotation.SuppressLint
import java.text.SimpleDateFormat
import java.util.*

object TimeUtility {

    /**
     * Formats a given UTC timestamp into a string with the pattern "HH:mm:ss".
     *
     * This method converts the UTC timestamp into a human-readable time format
     * using the device's default locale settings.
     *
     * @param utcTimestamp The UTC timestamp to format (milliseconds since epoch).
     * @return A string representing the formatted time in "HH:mm:ss" format.
     */
    fun getFormattedHhMmSs(utcTimestamp: Long): String {
        val formatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val date = Date(utcTimestamp)
        return formatter.format(date)
    }

    /**
     * Formats a given UTC timestamp into a string representing the day of the week.
     *
     * This method converts the UTC timestamp into the day of the week (e.g., "Mon", "Tue")
     * based on the device's default locale settings.
     *
     * @param utcTimestamp The UTC timestamp to format (milliseconds since epoch).
     * @return A string representing the day of the week in short form (e.g., "Mon").
     */
    fun getFormattedDay(utcTimestamp: Long): String {
        val formatter = SimpleDateFormat("E", Locale.getDefault())
        val date = Date(utcTimestamp)
        return formatter.format(date)
    }

    /**
     * Formats a given UTC timestamp into a date string with the pattern "MM/dd/yyyy".
     *
     * This method converts the UTC timestamp into a human-readable date format
     * using the device's default locale settings.
     *
     * @param utcTimestamp The UTC timestamp to format (milliseconds since epoch).
     * @return A string representing the formatted date in "MM/dd/yyyy" format.
     */
    fun getFormattedDate(utcTimestamp: Long): String {
        val formatter = SimpleDateFormat("MM/dd/yyyy", Locale.getDefault())
        val date = Date(utcTimestamp)
        return formatter.format(date)
    }

    /**
     * Converts a UTC date-time string into an epoch timestamp (seconds since epoch).
     *
     * This method parses a UTC date-time string formatted as "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
     * and converts it into epoch time. If the input string is null or cannot be parsed,
     * it returns 0L.
     *
     * @param utcString The UTC date-time string to convert.
     * @return The epoch timestamp in seconds, or 0L if the input is invalid.
     */
    fun convertToEpochTime(utcString: String?): Long {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
        dateFormat.timeZone = TimeZone.getTimeZone("UTC")

        if (utcString.isNullOrEmpty()) {
            return 0L
        }
        return try {
            val date = dateFormat.parse(utcString)
            date?.toInstant()?.epochSecond ?: 0L
        } catch (e: Exception) {
            // Handle parsing error
            0L
        }
    }

    /**
     * Retrieves the current date and time formatted for SQL queries.
     *
     * This method formats the current date and time into a string pattern
     * suitable for SQL datetime fields, using the "yyyy-MM-dd HH:mm:ss" format.
     *
     * @return A string representing the current date and time formatted for SQL.
     */
    fun getCurrentTimeForSQL(): String {
        // Create a SimpleDateFormat object with the desired date and time format
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

        // Get the current date and time
        val currentTime = Date()

        // Format the current time as a string
        val formattedTime = dateFormat.format(currentTime)

        return formattedTime
    }

    /**
     * Validates whether a given string is a correctly formatted UTC date-time string.
     *
     * This method attempts to parse the input string using the UTC format
     * "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'". It returns true if parsing is successful, false otherwise.
     *
     * @param utcString The UTC date-time string to validate.
     * @return True if the string is a valid UTC date-time format, false otherwise.
     */
    fun validateUTCString(utcString: String): Boolean {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
        dateFormat.timeZone = TimeZone.getTimeZone("UTC")

        return try {
            val date = dateFormat.parse(utcString)
            date != null
        } catch (e: Exception) {
            // Handle parsing error
            false
        }
    }

    /**
     * Generates a formatted string representing the current screen capture timestamp.
     *
     * This method creates a string using the pattern "yyyy_MM_dd_kk_mm_ss_SSS",
     * which can be used for uniquely naming screenshots or other time-sensitive files.
     *
     * @return A string representing the current timestamp formatted for screen captures.
     */
    @SuppressLint("SimpleDateFormat")
    fun getFormattedScreenCaptureTime(): String {
        val currentTime = Calendar.getInstance()
        val format = SimpleDateFormat("yyyy_MM_dd_kk_mm_ss_SSS")

        return format.format(currentTime.time)
    }

    /**
     * Calculates the start of the day for a given date and returns it as a string.
     *
     * This method computes the beginning of the day (00:00:00) for the provided date,
     * adjusting for the local timezone.
     *
     * @param time The date for which to calculate the start of the day.
     * @return A string representing the start of the day in ISO-8601 format.
     */
    fun getStartOfDay(time: Date): String {
        val cal = Calendar.getInstance(TimeZone.getDefault())
        cal.time = time // compute start of the day for the timestamp
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)

        return cal.time.toInstant().toString()
    }

    /**
     * Retrieves the current UTC timestamp as a Date object.
     *
     * This method gets the current date and time in UTC timezone.
     *
     * @return The current UTC timestamp as a Date object.
     */
    fun getCurrentTimestamp(): Date = Calendar.getInstance(TimeZone.getTimeZone("UTC")).time

    /**
     * Retrieves the current UTC timestamp in epoch seconds.
     *
     * This method gets the current date and time in UTC and converts it to epoch seconds.
     *
     * @return The current UTC timestamp in epoch seconds.
     */
    fun getCurrentTimestampEpoch(): Long = Calendar.getInstance(TimeZone.getTimeZone("UTC")).time.toInstant().epochSecond

    /**
     * Retrieves the current UTC timestamp in epoch milliseconds.
     *
     * This method gets the current date and time in UTC and converts it to epoch milliseconds.
     *
     * @return The current UTC timestamp in epoch milliseconds.
     */
    fun getCurrentTimestampEpochMillis(): Long = Calendar.getInstance(TimeZone.getTimeZone("UTC")).time.toInstant().toEpochMilli()

    /**
     * Retrieves the current timestamp based on the default device timezone as a Date object.
     *
     * This method gets the current date and time based on the device's default timezone.
     *
     * @return The current timestamp in the default timezone as a Date object.
     */
    fun getCurrentTimestampDefaultTimezone(): Date = Calendar.getInstance(TimeZone.getDefault()).time

    fun getCurrentTimestampDefaultTimezoneString(): String = getCurrentTimestampDefaultTimezone().time.toString()

    /**
     * Retrieves the current UTC timestamp as a string.
     *
     * This method gets the current date and time in UTC and converts it to a string.
     *
     * @return A string representing the current UTC timestamp.
     */
    fun getCurrentTimestampString(): String = getCurrentTimestamp().toString()
}
