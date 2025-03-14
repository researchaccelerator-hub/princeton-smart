package com.screenlake.recorder.behavior.behaviors.handlers

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import android.webkit.URLUtil
import com.screenlake.recorder.constants.ConstantSettings
import com.screenlake.data.database.entity.AccessibilityEventEntity
import com.screenlake.data.enums.BehavioralEvents
import com.screenlake.data.repository.GeneralOperationsRepository
import com.screenlake.recorder.services.util.ScreenshotData
import com.screenlake.recorder.services.TouchAccessibilityService
import com.screenlake.recorder.utilities.TimeUtility
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AllTextHandler @Inject constructor(
    private val generalOperationsRepository: GeneralOperationsRepository
) : EventHandler {
    private var previousKey = "";

    override fun canHandleEvent(event: AccessibilityEventEntity): Boolean {
        return when(event.behavorType) {
            BehavioralEvents.SESSION_OVER -> true
            else -> false
        }
    }

    override fun handleEvent(event: AccessibilityEventEntity) {
        when(event.behavorType) {
            BehavioralEvents.SESSION_OVER -> {

            }
            else -> {

            }
        }
    }

    override fun canHandleEvent(event: AccessibilityNodeInfo): Boolean {
        return true
    }

    override fun handleEvent(event: AccessibilityNodeInfo) {
        extractTextFromRoot(event)
    }

    private fun extractTextFromRoot(rootNode: AccessibilityNodeInfo?) {
        Timber.tag("AccessibilityEvent").d("Getting screen components.")

        if (rootNode != null) {
            val packageName = rootNode.packageName.toString()
            val moveForward = ConstantSettings.RESTRICTED_APPS.contains(packageName)

            if (moveForward) return

            val result = mutableListOf<AccessibilityEventEntity>()
            var groupdId = UUID.randomUUID().toString()
            val currentTime = TimeUtility.getCurrentTimestampEpochMillis()

            val rect = Rect()
            rootNode.getBoundsInScreen(rect)

            val maxBot = Math.abs(rect.bottom)
            val maxRight = Math.abs(rect.right)

            var resultText = mutableListOf<String>()
            traverseNode(rootNode, result, resultText, maxBot, maxRight)
            rootNode.recycle()

            handleScreenText(result, resultText, packageName, currentTime)
        }
    }

    private fun handleScreenText(
        result: MutableList<AccessibilityEventEntity>,
        resultText: MutableList<String>,
        packageName: String,
        currentTime: Long
    ) {
        val joinedText = resultText.joinToString()

        if (resultText.isNotEmpty() && resultText.joinToString() != previousKey) {
            previousKey = joinedText
            save(
                AccessibilityEventEntity(
                    user = TouchAccessibilityService.user?.emailHash,
                    packageName = packageName,
                    appIntervalId = TouchAccessibilityService.appIntervalId,
                    text = ScreenshotData.ocrCleanUp(joinedText),
                    eventTime = currentTime,
                    eventType = "SCREEN_TEXT",
                    accessibilitySessionId = TouchAccessibilityService.appAccessibilitySessionId
                )
            )
        }
    }

    private fun traverseNode(node: AccessibilityNodeInfo, result:MutableList<AccessibilityEventEntity>, resultText:MutableList<String>, maxBot:Int, maxRight:Int) {
        if (node.childCount == 0) {
            // Leaf node, retrieve text
            val text = node.text
            val currentTime = TimeUtility.getCurrentTimestampEpochMillis()
            if (!text.isNullOrEmpty()) {
                // Do something with the text
                // println(">>> ${node.toString()} <<<")
                if(text.contains(".com") && node.className.contains("android.widget.EditText")){
                    println("***** ${URLUtil.isValidUrl("http://" + text.toString())} **** $text ****")
                    val currentUrl = text.toString()
                    if (currentUrl != TouchAccessibilityService.prevUrl && URLUtil.isValidUrl("http://" + currentUrl)){
                        save(
                            AccessibilityEventEntity(
                                user = TouchAccessibilityService.user?.emailHash,
                                eventType = "URL",
                                text = currentUrl,
                                eventTime = currentTime,
                                packageName = node.packageName.toString(),
                                accessibilitySessionId = TouchAccessibilityService.appAccessibilitySessionId,
                                appIntervalId = TouchAccessibilityService.appIntervalId
                            )
                        )
                        TouchAccessibilityService.prevUrl = currentUrl
                    }

                }
                val rect = android.graphics.Rect()
                node.getBoundsInScreen(rect)

                if ((rect.left in 0..maxRight)
                    && (rect.top in 0..maxBot)
                    && (rect.right in 0..maxBot)
                    && (rect.bottom in 0..maxBot)){
                    val combined = text.toString() + "\n"
                    if (node.className?.contains("android.widget.Image") == true){
                        if (TouchAccessibilityService.prevMeta != text.toString()){
                            result.add(AccessibilityEventEntity().apply {
                                this.user = TouchAccessibilityService.user?.emailHash
                                this.eventType = "IMAGE_METADATA"
                                this.text = text.toString()
                                this.eventTime = currentTime
                                this.packageName = node.packageName.toString()
                                this.accessibilitySessionId =
                                    TouchAccessibilityService.appAccessibilitySessionId
                                this.appIntervalId = TouchAccessibilityService.appIntervalId
                            })
                            TouchAccessibilityService.prevMeta = text.toString()
                        }
                    }else{
                        resultText.add(combined)
                    }

                }
            }
        } else {
            // Parent node, traverse children
            for (i in 0 until node.childCount) {
                val childNode = node.getChild(i)
                childNode ?: return

                traverseNode(childNode, result, resultText, maxBot, maxRight)
                childNode.recycle()
            }
        }
    }


    fun save(accessibilityEvent: AccessibilityEventEntity) {
        generalOperationsRepository.save(accessibilityEvent)
    }

    suspend fun save(accessibilityEvents: List<AccessibilityEventEntity>) {
        generalOperationsRepository.save(accessibilityEvents)
    }
}