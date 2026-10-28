package com.thirdeye.app

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    private val BG = Color.parseColor("#ECE8E1"); private val CARD = Color.parseColor("#F7F4EF")
    private val INK = Color.parseColor("#3D4A47"); private val SUB = Color.parseColor("#7C8885")
    private val SAGE = Color.parseColor("#8FA38B"); private val ROSE = Color.parseColor("#B88A93")
    private lateinit var p: SharedPreferences
    private lateinit var btn: TextView

    private val perms = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (granted()) start() else toast("Camera and microphone permission are needed")
    }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        p = getSharedPreferences("te", MODE_PRIVATE)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(28), dp(20), dp(28)) }
        root.addView(txt("Third Eye", 30f, INK, true))
        root.addView(txt("Quiet video recording, screen off.", 15f, SUB, false).also { it.setPadding(0, dp(2), 0, dp(18)) })

        btn = txt("", 18f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER; setOnClickListener { toggle() }
        }
        root.addView(btn, LinearLayout.LayoutParams(MATCH_PARENT, dp(68)).apply { bottomMargin = dp(16) })

        seg(root, "Camera", listOf("Back", "Front"), "cam", 0)
        seg(root, "Quality", listOf("480p", "720p", "1080p"), "res", 1)
        seg(root, "Auto-split every", listOf("Off", "5 min", "10 min", "20 min"), "seg", 0)
        seg(root, "Start delay (time to pocket the phone)", listOf("None", "5 s", "10 s"), "delay", 0)
        card(root).apply {
            addView(SwitchCompat(this@MainActivity).apply {
                text = "Record sound"; setTextColor(INK); isChecked = p.getBoolean("mic", true)
                setOnCheckedChangeListener { _, c -> p.edit().putBoolean("mic", c).apply() }
            })
        }
        val open = txt("Open my recordings", 15f, INK, true).apply {
            gravity = Gravity.CENTER; background = bg(Color.TRANSPARENT, 16, SAGE)
            setOnClickListener { startActivity(Intent(Intent.ACTION_VIEW, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)) }
        }
        root.addView(open, LinearLayout.LayoutParams(MATCH_PARENT, dp(52)).apply { topMargin = dp(4) })
        root.addView(txt("Android always shows a notification and camera indicator while recording. " +
            "Only record where you have everyone's consent.", 12f, SUB, false).also { it.setPadding(dp(4), dp(16), dp(4), 0) })
        setContentView(ScrollView(this).apply { setBackgroundColor(BG); addView(root) })
    }

    override fun onResume() { super.onResume(); refresh() }

    private fun refresh() {
        val on = RecorderService.running
        btn.text = if (on) "■  Stop recording" else "●  Start recording"
        btn.background = bg(if (on) ROSE else SAGE, 24)
    }

    private fun toggle() {
        if (RecorderService.running) {
            startService(Intent(this, RecorderService::class.java).setAction(RecorderService.STOP))
            btn.postDelayed({ refresh() }, 600)
        } else if (granted()) start()
        else perms.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS))
    }

    private fun start() {
        ContextCompat.startForegroundService(this, Intent(this, RecorderService::class.java))
        toast("Recording is starting. You can turn the screen off.")
        btn.postDelayed({ refresh() }, 600)
    }

    private fun granted() = listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        .all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }

    // ---- tiny UI helpers ----
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_LONG).show()
    private fun bg(c: Int, r: Int, stroke: Int? = null) = GradientDrawable().apply {
        setColor(c); cornerRadius = dp(r).toFloat(); if (stroke != null) setStroke(dp(1), stroke)
    }
    private fun txt(t: String, sp: Float, c: Int, bold: Boolean) = TextView(this).apply {
        text = t; textSize = sp; setTextColor(c); if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }
    private fun card(parent: LinearLayout) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(14), dp(16), dp(10)); background = bg(CARD, 20)
        parent.addView(this, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(12) })
    }
    private fun seg(parent: LinearLayout, title: String, opts: List<String>, key: String, def: Int) {
        val c = card(parent)
        c.addView(txt(title, 13f, SUB, false))
        val g = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL }
        opts.forEachIndexed { i, o ->
            g.addView(RadioButton(this).apply {
                id = i + 1; text = o; setTextColor(INK); textSize = 14f
                buttonTintList = ColorStateList.valueOf(SAGE)
            }, RadioGroup.LayoutParams(0, WRAP_CONTENT, 1f))
        }
        g.check(p.getInt(key, def) + 1)
        g.setOnCheckedChangeListener { _, id -> p.edit().putInt(key, id - 1).apply() }
        c.addView(g)
    }
}
