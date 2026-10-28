package com.thirdeye.app

import android.annotation.SuppressLint
import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.os.*
import android.provider.MediaStore
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import java.text.SimpleDateFormat
import java.util.*

class RecorderService : LifecycleService() {
    companion object { @Volatile var running = false; const val STOP = "STOP" }

    private val h = Handler(Looper.getMainLooper())
    private var vc: VideoCapture<Recorder>? = null
    private var rec: Recording? = null
    private var stopping = false
    private var wake: PowerManager.WakeLock? = null
    private var part = 0
    private val t0 = System.currentTimeMillis()

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        super.onStartCommand(i, f, id)
        if (i?.action == STOP) { if (running) stopAll() else stopSelf(); return START_NOT_STICKY }
        if (running) return START_NOT_STICKY
        running = true; stopping = false
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel("te", "Third Eye", NotificationManager.IMPORTANCE_LOW))
        startForeground(1, notif("Getting ready…"),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        wake = (getSystemService(POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "thirdeye:rec").apply { acquire(6 * 60 * 60 * 1000L) }
        val p = getSharedPreferences("te", MODE_PRIVATE)
        h.postDelayed({ bind(p) }, intArrayOf(0, 5, 10)[p.getInt("delay", 0)] * 1000L)
        return START_NOT_STICKY
    }

    private fun notif(text: String): Notification = NotificationCompat.Builder(this, "te")
        .setSmallIcon(android.R.drawable.ic_menu_camera)
        .setContentTitle("Third Eye is recording").setContentText(text)
        .setOngoing(true).setUsesChronometer(true).setWhen(t0)
        .setContentIntent(PendingIntent.getActivity(this, 1, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
        .addAction(0, "Stop", PendingIntent.getService(this, 0,
            Intent(this, RecorderService::class.java).setAction(STOP), PendingIntent.FLAG_IMMUTABLE))
        .build()

    private fun bind(p: SharedPreferences) {
        if (stopping) return
        val q = arrayOf(Quality.SD, Quality.HD, Quality.FHD)[p.getInt("res", 1)]
        val sel = if (p.getInt("cam", 0) == 0) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
        val fut = ProcessCameraProvider.getInstance(this)
        fut.addListener({
            try {
                val r = Recorder.Builder()
                    .setQualitySelector(QualitySelector.from(q, FallbackStrategy.lowerQualityOrHigherThan(q))).build()
                vc = VideoCapture.withOutput(r)
                fut.get().unbindAll()
                fut.get().bindToLifecycle(this, sel, vc!!)
                next(p)
            } catch (e: Exception) { finish() }
        }, ContextCompat.getMainExecutor(this))
    }

    @SuppressLint("MissingPermission")
    private fun next(p: SharedPreferences) {
        if (stopping) return
        h.removeCallbacksAndMessages(null)
        val cv = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "ThirdEye_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()))
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/ThirdEye")
        }
        val o = MediaStoreOutputOptions.Builder(contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(cv).setFileSizeLimit(3_800_000_000L).build()
        var pr = vc!!.output.prepareRecording(this, o)
        if (p.getBoolean("mic", true)) pr = pr.withAudioEnabled()
        part++
        getSystemService(NotificationManager::class.java).notify(1, notif("Part $part · tap to open, or Stop"))
        rec = pr.start(ContextCompat.getMainExecutor(this)) { e ->
            if (e is VideoRecordEvent.Status) {
                if (Environment.getExternalStorageDirectory().usableSpace < 500_000_000L) stopAll()
            } else if (e is VideoRecordEvent.Finalize) {
                rec = null
                val ok = e.error == VideoRecordEvent.Finalize.ERROR_NONE ||
                        e.error == VideoRecordEvent.Finalize.ERROR_FILE_SIZE_LIMIT_REACHED
                if (!stopping && ok) next(p) else finish()
            }
        }
        val m = intArrayOf(0, 5, 10, 20)[p.getInt("seg", 0)]
        if (m > 0) h.postDelayed({ rec?.stop() }, m * 60_000L)
    }

    private fun stopAll() {
        if (stopping) return
        stopping = true; h.removeCallbacksAndMessages(null)
        val r = rec
        if (r != null) r.stop() else finish()
    }

    private fun finish() {
        running = false
        wake?.let { if (it.isHeld) it.release() }
        stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
    }

    override fun onDestroy() { running = false; super.onDestroy() }
}
