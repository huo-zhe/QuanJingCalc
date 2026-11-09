package com.rshock.quanjing.auto

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat

class ScreenCaptureService : Service() {

    companion object {
        const val ACTION_START = "start_capture"
        const val ACTION_STOP = "stop_capture"
        const val EXTRA_CODE = "result_code"
        const val EXTRA_DATA = "result_data"
        private const val TAG = "QJ_Capture"

        @Volatile var instance: ScreenCaptureService? = null
    }

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var width = 0
    private var height = 0
    private var density = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        startForegroundNotice()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val code = intent.getIntExtra(EXTRA_CODE, 0)
                val data = intent.getParcelableExtra<Intent>(EXTRA_DATA)
                if (data != null) {
                    // ★ 关键：先清理旧会话
                    releaseProjection()
                    initProjection(code, data)
                }
            }
            ACTION_STOP -> {
                releaseProjection()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun initProjection(code: Int, data: Intent) {
        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = mpm.getMediaProjection(code, data)

        if (projection == null) {
            Log.e(TAG, "getMediaProjection returned null")
            return
        }

        // Android 14+ 必须注册 callback
        projection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                releaseProjection()
            }
        }, null)

        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        width = metrics.widthPixels
        height = metrics.heightPixels
        density = metrics.densityDpi

        try {
            imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            virtualDisplay = projection!!.createVirtualDisplay(
                "qj-capture", width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader!!.surface, null, null
            )
        } catch (e: Exception) {
            Log.e(TAG, "createVirtualDisplay failed", e)
            releaseProjection()
        }
    }

    private fun releaseProjection() {
        try { virtualDisplay?.release() } catch (_: Exception) {}
        virtualDisplay = null
        try { imageReader?.close() } catch (_: Exception) {}
        imageReader = null
        try { projection?.stop() } catch (_: Exception) {}
        projection = null
    }

    fun capture(): Bitmap? {
        val reader = imageReader ?: return null

        var image: android.media.Image? = null
        var retry = 0
        while (image == null && retry < 20) {
            image = reader.acquireLatestImage()
            if (image == null) {
                try { Thread.sleep(10) } catch (_: Exception) {}
                retry++
            }
        }

        if (image == null) return null

        // ★ 这里必须有 return
        return try {
            val plane = image.planes[0]
            val buffer = plane.buffer
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val rowPadding = rowStride - pixelStride * image.width

            val bmpWidth = image.width + rowPadding / pixelStride
            val bmp = Bitmap.createBitmap(bmpWidth, image.height, Bitmap.Config.ARGB_8888)
            bmp.copyPixelsFromBuffer(buffer)

            if (rowPadding != 0) {
                val cropped = Bitmap.createBitmap(bmp, 0, 0, image.width, image.height)
                bmp.recycle()
                cropped
            } else {
                bmp
            }
        } catch (e: Exception) {
            android.util.Log.e("QJ", "capture failed", e)
            null
        } finally {
            try { image.close() } catch (_: Exception) {}
        }
    }
    override fun onDestroy() {
        releaseProjection()
        instance = null
        super.onDestroy()
    }

    private fun startForegroundNotice() {
        val channelId = "qj_capture"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            val ch = NotificationChannel(
                channelId, "截屏服务", NotificationManager.IMPORTANCE_LOW
            )
            nm.createNotificationChannel(ch)
        }
        val notif = NotificationCompat.Builder(this, channelId)
            .setContentTitle("全境征才助手运行中")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build()
        startForeground(1001, notif)
    }
}