package com.example.wordunlock

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.app.KeyguardManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.example.wordunlock.models.WordDefinition
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.random.Random

class WordUnlockService : AccessibilityService() {

    /*override fun onBind(intent: Intent): IBinder {
        TODO("Return the communication channel to the service.")
    }*/

    private var isLockScreenShown = false
    private var currentActivity: Activity? = null
    private var isOpenActivity = false
    private var isUnlockTriggered = false // 新增变量追踪屏幕解锁后是否已触发
    private var wasScreenUnlocked = false // 上次事件时屏幕是否解锁
    private var wasOnDesktop = false // 追踪是否之前处于桌面
    private var unlockToDesktopTriggered = false // 解锁后到桌面的逻辑是否已触发
    private var lastUnlockTime: Long = 0 // 上次解锁时间
    private var lastNonDesktopPackageName: String? = null // 上次非桌面应用的包名
    private var firstInstallTriggered = false // 首次安装后回到桌面的逻辑是否已触发
    private var firstDesktopReturnHandled = false // 首次从非桌面应用返回桌面的逻辑是否已处理
    private var firstDesktopInteractionHandled = false // 首次从非桌面应用返回桌面的逻辑是否已处理
    // 在 WordUnlockService 中注册事件类型
    override fun onServiceConnected() {
        val info = AccessibilityServiceInfo()

        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        serviceInfo = info
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val eventType = event.eventType
        val packageName = event.packageName.toString()
        val currentTime = System.currentTimeMillis()


        // 检查是否解锁
        val isUnlockEvent = eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && isUnlockEvent(event)

        // 检查是否切换到桌面
        val isSwitchToDesktop = isLauncherPackage(packageName)
        when {
             isUnlockEvent  && isSwitchToDesktop -> {
                lastUnlockTime = currentTime // 记录解锁时间
                if (!firstDesktopReturnHandled && isSwitchToDesktop) {
                    // 如果是首次安装后首次解锁切回桌面，触发弹窗
                    showWordInputActivity()
                    firstDesktopReturnHandled = true
                }

            }
            isSwitchToDesktop && lastUnlockTime > 0 && currentTime - lastUnlockTime < 500 -> {
                // 从非桌面应用切回桌面，并且在解锁后的短时间内
                if (!firstDesktopReturnHandled || lastNonDesktopPackageName != null) {
                    // 首次或非首次（但符合解锁后短时间内）从非桌面应用返回桌面，触发弹窗
                    showWordInputActivity()
                    if (!firstDesktopReturnHandled) {
                        firstDesktopReturnHandled = true
                    }
                }
            }

            eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && !isLauncherPackage(packageName) -> {
                // 记录离开桌面时的包名，用于后续判断是否是从非桌面应用返回桌面
                lastNonDesktopPackageName = packageName
            }
        }
        // 更新屏幕解锁状态
        wasScreenUnlocked = isSwitchToDesktop
    }

    private fun isUnlockEvent(event: AccessibilityEvent): Boolean {
        // 实现检查是否解锁的逻辑，例如通过KeyguardManager或检查特定事件属性
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        return !keyguardManager.isKeyguardLocked
    }
    private fun isLauncherPackage(packageName: String): Boolean {
        // 获取设备的 Launcher 应用的包名
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolveInfo = packageManager.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
        val launcherPackageName = resolveInfo?.activityInfo?.packageName
        return launcherPackageName != null && packageName == launcherPackageName
    }

    private fun isScreenUnlocked(): Boolean {
        // 实现检查屏幕是否解锁的逻辑
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        return !keyguardManager.isKeyguardLocked
    }

    private fun isKeyguardLocked(): Boolean {
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        return keyguardManager.isKeyguardLocked
    }

    override fun onInterrupt() {

    }

    private fun showWordInputActivity() {
       // if (currentActivity == null) {
            val intent = Intent(this, WordUnlockForegroundService::class.java)
            val randomWordDefinition = getRandomWordDefinitionFromJson(this)
            randomWordDefinition?.let {
                val word = it.word
                var definition = it.definition

                intent.putExtra("word", word) // 传递单词数据
                intent.putExtra("definition", definition) // 传递单词定义
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                startService(intent)
            }
       // }
    }


    fun getRandomWordDefinitionFromJson(context: Context): WordDefinition? {
        val resourceId = context.resources.getIdentifier("sixlevel", "raw", context.packageName)
        if (resourceId == 0) {
            // 文件不存在或命名错误
            return null
        }

        val inputStream = context.resources.openRawResource(resourceId)
        val reader = BufferedReader(InputStreamReader(inputStream))
        val sb = StringBuilder()
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            sb.append(line)
        }

        val gson = Gson()
        val type = object : TypeToken<List<WordDefinition>>() {}.type
        val wordDefinitions: List<WordDefinition> = gson.fromJson(sb.toString(), type)

        val random = java.util.Random()
        return wordDefinitions.randomOrNull() ?: return null
    }
}