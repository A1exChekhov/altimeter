package com.chelmodeev.altimeter.watch

import android.content.Context
import com.chelmodeev.altimeter.BuildConfig
import com.chelmodeev.altimeter.R
import com.huawei.wearengine.HiWear
import com.huawei.wearengine.WearEngineException
import com.huawei.wearengine.auth.AuthCallback
import com.huawei.wearengine.auth.Permission
import com.huawei.wearengine.device.Device
import com.huawei.wearengine.p2p.Message
import com.huawei.wearengine.p2p.SendCallback

/**
 * Канал «в своё приложение на часах» через Huawei Wear Engine (P2P).
 *
 * Требования (см. README): Huawei Health на телефоне, регистрация приложения
 * в AppGallery Connect с доступом Wear Engine и приложение-приёмник на часах
 * (пакет/отпечаток задаются в gradle.properties).
 */
class WearEngineBridge(private val appContext: Context) {

    /** Строка статуса для UI. */
    var onStatus: ((String) -> Unit)? = null

    /** uiContext — Activity, чтобы Huawei Health мог показать диалог авторизации. */
    fun sendJson(uiContext: Context, payload: String) {
        runCatching { checkAuthThenSend(uiContext, payload) }
            .onFailure { report(it) }
    }

    private fun checkAuthThenSend(ui: Context, payload: String) {
        onStatus?.invoke(appContext.getString(R.string.we_sending))
        val authClient = HiWear.getAuthClient(ui)
        authClient.checkPermission(Permission.DEVICE_MANAGER)
            .addOnSuccessListener { granted ->
                if (granted == true) findDeviceAndSend(ui, payload) else requestAuth(ui, payload)
            }
            .addOnFailureListener { report(it) }
    }

    private fun requestAuth(ui: Context, payload: String) {
        val authClient = HiWear.getAuthClient(ui)
        authClient.requestPermission(
            object : AuthCallback {
                override fun onOk(permissions: Array<Permission>) = findDeviceAndSend(ui, payload)
                override fun onCancel() {
                    onStatus?.invoke(appContext.getString(R.string.we_auth_cancelled))
                }
            },
            Permission.DEVICE_MANAGER
        ).addOnFailureListener { report(it) }
    }

    private fun findDeviceAndSend(ui: Context, payload: String) {
        HiWear.getDeviceClient(ui).bondedDevices
            .addOnSuccessListener { devices ->
                val device = devices?.firstOrNull { it.isConnected } ?: devices?.firstOrNull()
                if (device == null) {
                    onStatus?.invoke(appContext.getString(R.string.we_no_devices))
                } else {
                    sendTo(ui, device, payload)
                }
            }
            .addOnFailureListener { report(it) }
    }

    private fun sendTo(ui: Context, device: Device, payload: String) {
        val p2p = HiWear.getP2pClient(ui)
        p2p.setPeerPkgName(BuildConfig.WATCH_APP_PACKAGE)
        if (BuildConfig.WATCH_APP_FINGERPRINT.isNotBlank()) {
            p2p.setPeerFingerPrint(BuildConfig.WATCH_APP_FINGERPRINT)
        }
        val message = Message.Builder()
            .setPayload(payload.toByteArray(Charsets.UTF_8))
            .build()
        p2p.send(
            device, message,
            object : SendCallback {
                override fun onSendResult(resultCode: Int) {
                    if (resultCode == 207) {
                        onStatus?.invoke(appContext.getString(R.string.we_sent))
                    } else {
                        onStatus?.invoke(
                            appContext.getString(R.string.we_error, resultCode.toString())
                        )
                    }
                }

                override fun onSendProgress(progress: Long) = Unit
            }
        ).addOnFailureListener { report(it) }
    }

    private fun report(e: Throwable) {
        val code = (e as? WearEngineException)?.errorCode?.toString()
            ?: e.message ?: e.javaClass.simpleName
        onStatus?.invoke(appContext.getString(R.string.we_error, code))
    }
}
