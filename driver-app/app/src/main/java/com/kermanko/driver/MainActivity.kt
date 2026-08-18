package com.kermanko.driver

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class MainActivity : AppCompatActivity() {

    private lateinit var lineListContainer: LinearLayout
    private lateinit var statusText: TextView
    private lateinit var startButton: Button
    private lateinit var stopButton: Button

    private var selectedLineId: String? = null

    private val permissionsNeeded = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        lineListContainer = findViewById(R.id.lineListContainer)
        statusText = findViewById(R.id.statusText)
        startButton = findViewById(R.id.startButton)
        stopButton = findViewById(R.id.stopButton)

        loadLinesFromFirebase()

        startButton.setOnClickListener {
            val lineId = selectedLineId
            if (lineId == null) {
                Toast.makeText(this, "اول یه خط انتخاب کن", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (hasAllPermissions()) {
                startTracking(lineId)
            } else {
                ActivityCompat.requestPermissions(this, permissionsNeeded, 100)
            }
        }

        stopButton.setOnClickListener { stopTracking() }
    }

    // این تابع لیست خط‌هایی که مدیر از قبل تو پنل ادمین ثبت کرده رو می‌خونه
    // راننده هیچی تایپ نمی‌کنه، فقط رو دکمه‌ی خط خودش ضربه می‌زنه
    private fun loadLinesFromFirebase() {
        val ref = FirebaseDatabase.getInstance().reference.child("driver_profiles")
        ref.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                lineListContainer.removeAllViews()
                if (!snapshot.exists()) {
                    val emptyText = TextView(this@MainActivity)
                    emptyText.text = "هنوز هیچ خطی توسط مدیر ثبت نشده"
                    lineListContainer.addView(emptyText)
                    return
                }

                for (child in snapshot.children) {
                    val lineId = child.key ?: continue
                    val btn = Button(this@MainActivity)
                    btn.text = "خط $lineId"
                    btn.setOnClickListener { selectLine(lineId, btn) }
                    val params = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    params.bottomMargin = 16
                    btn.layoutParams = params
                    lineListContainer.addView(btn)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@MainActivity, "خطا در بارگذاری خط‌ها", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun selectLine(lineId: String, clickedButton: Button) {
        selectedLineId = lineId
        // رنگ همه‌ی دکمه‌ها رو ریست کن، فقط انتخاب‌شده رنگی بشه
        for (i in 0 until lineListContainer.childCount) {
            (lineListContainer.getChildAt(i) as? Button)?.setBackgroundColor(Color.LTGRAY)
        }
        clickedButton.setBackgroundColor(Color.parseColor("#0e8a8a"))
        clickedButton.setTextColor(Color.WHITE)

        statusText.text = "خط $lineId انتخاب شد"
        startButton.visibility = android.view.View.VISIBLE
    }

    private fun hasAllPermissions(): Boolean {
        return permissionsNeeded.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.isNotEmpty() &&
            grantResults.all { it == PackageManager.PERMISSION_GRANTED }
        ) {
            selectedLineId?.let { startTracking(it) }
        } else {
            Toast.makeText(this, "بدون اجازه‌ی موقعیت مکانی، اپ نمی‌تونه کار کنه", Toast.LENGTH_LONG).show()
        }
    }

    private fun startTracking(lineId: String) {
        val intent = Intent(this, LocationService::class.java)
        intent.putExtra("lineId", lineId)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        statusText.text = "در حال ارسال موقعیت برای خط $lineId"
        startButton.visibility = android.view.View.GONE
        stopButton.visibility = android.view.View.VISIBLE
    }

    private fun stopTracking() {
        stopService(Intent(this, LocationService::class.java))
        statusText.text = "متوقف شد"
        startButton.visibility = android.view.View.VISIBLE
        stopButton.visibility = android.view.View.GONE
    }
}
