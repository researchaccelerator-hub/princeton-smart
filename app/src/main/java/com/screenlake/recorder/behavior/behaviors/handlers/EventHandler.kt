package com.screenlake.recorder.behavior.behaviors.handlers

import android.view.accessibility.AccessibilityNodeInfo
import com.screenlake.data.database.entity.AccessibilityEventEntity

interface EventHandler {
    fun canHandleEvent(event: AccessibilityEventEntity): Boolean {
        return true
    }

    fun handleEvent(event: AccessibilityEventEntity){

    }

    fun handleEvent(event: AccessibilityNodeInfo){

    }

    fun canHandleEvent(event: AccessibilityNodeInfo): Boolean{
        return false
    }
}