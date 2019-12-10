package com.specialprojects.experiments.envelopecall

import android.Manifest
import android.animation.Animator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.TransitionDrawable
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.telecom.TelecomManager
import android.telecom.VideoProfile
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.animation.addListener
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.Observer
import timber.log.Timber
import java.util.*


class MainActivity : AppCompatActivity() {
    private val REQUEST_CODE_SET_DEFAULT_DIALER: Int = 0x1
    private val REQUEST_CALL_PHONE: Int = 0x2

    private val callBtnView by bindView<Button>(R.id.call)
    private val numberView by bindView<TextView>(R.id.number)
    private val clockBtnView by bindView<Button>(R.id.clock)

    private val handler = Handler()

    private lateinit var proximitySensor: ProximitySensor

    private val idActionMap = mapOf(
        R.id.one to "1",
        R.id.two to "2",
        R.id.three to "3",
        R.id.four to "4",
        R.id.five to "5",
        R.id.six to "6",
        R.id.seven to "7",
        R.id.eight to "8",
        R.id.nine to "9",
        R.id.zero to "0",
        R.id.star to "*",
        R.id.hash to "#"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        proximitySensor = ProximitySensor(this)
        proximitySensor.state.observe(this, Observer {
            when(it) {
                ProximityState.Near -> {
                    setReadyStyles()
                }
                ProximityState.Far -> {
                    setDefaultStyles()
                }
            }
        })

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION // hide nav bar
                or View.SYSTEM_UI_FLAG_FULLSCREEN // hide status bar
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)


        val numberView = findViewById<TextView>(R.id.number)

        idActionMap.keys.forEach { id ->
            findViewById<Button>(id).setOnClickListener {
                numberView.append(idActionMap[id])
                Timber.d("Current number: ${numberView.text}")
            }
        }

        numberView.addTextChangedListener(afterTextChanged = { result ->
            result?.let {
                callBtnView.alpha = if (it.isNotEmpty()) 1F else 0F
            }
        })

        defaultCallHandle()

        (applicationContext as EnvelopeCallApp).callState.observe(this, Observer { callState ->
            when(callState) {
                CallState.Default -> {
                    callBtnView.alpha = 0F
                    (callBtnView.background as TransitionDrawable).resetTransition()
                    currentAnimation?.end()
                    defaultCallHandle()
                }

                is CallState.Ringing -> {
                    createPulseAnimation()

                    callBtnView.apply {
                        setOnClickListener {
                            callState.call.answer(VideoProfile.STATE_AUDIO_ONLY)
                            currentAnimation?.end()
                        }
                        setOnLongClickListener {
                            callState.call.disconnect()
                            currentAnimation?.end()
                            true
                        }
                    }

                    currentAnimation?.start()
                }
                is CallState.Dialing -> {
                    callBtnView.apply {
                        setOnClickListener {
                            callState.call.disconnect()
                        }
                    }
                }
                is CallState.Active -> {
                    callBtnView.apply {
                        callBtnView.setBackgroundResource(R.drawable.call_button_state)
                        (background as TransitionDrawable).startTransition(300)
                        setOnClickListener {
                            callState.call.disconnect()
                        }
                    }

                    currentAnimation?.end()
                }
            }
        })

        offerReplacingDefaultDialer()

        clockBtnView.setOnClickListener {
            playAnimation()
        }
    }

    private fun setDefaultStyles() {
        idActionMap.keys.forEach { id ->
            findViewById<Button>(id).setBackgroundResource(R.drawable.btn_dial_background)
        }

        clockBtnView.setBackgroundResource(R.drawable.btn_clock_background)
    }

    private fun setReadyStyles() {
        idActionMap.keys.forEach { id ->
            findViewById<Button>(id).setBackgroundResource(R.drawable.btn_dial_background_ready)
        }

        clockBtnView.setBackgroundResource(R.drawable.btn_clock_background_ready)
    }

    private fun dialUpAnimation(id: Int) {
        val button = findViewById<Button>(id)
        button.isPressed = true
        button.isPressed = false
    }

    private fun playAnimation() {
        if (clockBtnView.isSelected) return

        clockBtnView.isSelected = true

        val rightNow = Calendar.getInstance()
        val currentHourIn24Format = rightNow.get(Calendar.HOUR_OF_DAY)
        val currentMinutes = rightNow.get(Calendar.MINUTE)

        val hour = if(currentHourIn24Format < 10) "0$currentHourIn24Format" else currentHourIn24Format.toString()
        val minutes = if(currentMinutes < 10) "0$currentMinutes" else currentMinutes.toString()

        val chars = "$hour$minutes".toCharArray()

        val listIds = mutableListOf<Int>()

        chars.forEach {
            Timber.d(it.toString())
            val id = idActionMap.entries.first { (_, value) -> value == it.toString() }

            listIds.add(id.key)
        }

        dialUpAnimation(listIds[0])

        handler.postDelayed({
            dialUpAnimation(listIds[1])
        }, 300)

        handler.postDelayed({
            dialUpAnimation(listIds[2])
        }, 800)

        handler.postDelayed({
            dialUpAnimation(listIds[3])
        }, 1100)

        handler.postDelayed({
            dialUpAnimation(listIds[0])
        }, 2000)

        handler.postDelayed({
            dialUpAnimation(listIds[1])
        }, 2300)

        handler.postDelayed({
            dialUpAnimation(listIds[2])
        }, 2800)

        handler.postDelayed({
            dialUpAnimation(listIds[3])
        }, 3100)

        handler.postDelayed({
            clockBtnView.isSelected = false
        }, 3500)
    }

    @SuppressLint("MissingPermission")
    private fun defaultCallHandle() {
        callBtnView.alpha = 0F

        numberView.text = ""

        callBtnView.apply {
            alpha = 0F
            setOnClickListener {
                val number = numberView.text.toString()
                Timber.d("Calling number: $number")
                val uri = "tel:$number".toUri()

                if (checkPermissions()) {
                    startActivity(Intent(Intent.ACTION_CALL, uri))
                }
            }
            setOnLongClickListener {
                numberView.text = ""
                true
            }
        }
    }

    private fun checkPermissions(): Boolean {
        Timber.d(" Checking permissions.")

        return if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CALL_PHONE
        ) != PackageManager.PERMISSION_GRANTED) {
            Timber.i("Call Phone permissions has NOT been granted. Requesting permissions.")
            requestPermissions()
            false
        } else {
            Timber.i("Contact permissions have already been granted")
            true
        }
    }

    private fun requestPermissions() { // BEGIN_INCLUDE(contacts_permission_request)
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CALL_PHONE)) {
            Timber.i("Displaying call permission rationale to provide additional context.")
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CALL_PHONE), REQUEST_CALL_PHONE)
        }
    }

    @SuppressLint("WrongConstant")
    private fun offerReplacingDefaultDialer() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(Context.ROLE_SERVICE) as RoleManager
            val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
            startActivityForResult(intent, REQUEST_CODE_SET_DEFAULT_DIALER)
        } else {
            val telecomManager = getSystemService(Context.TELECOM_SERVICE) as TelecomManager

            if (telecomManager.defaultDialerPackage !== packageName) {
                val changeDialer = Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER)
                changeDialer.putExtra(
                    TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME,
                    packageName
                )
                startActivity(changeDialer)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val message = when (resultCode) {
            RESULT_OK -> "User accepted request to become default dialer"
            RESULT_CANCELED -> "User declined request to become default dialer"
            else -> "Unexpected result code $resultCode"
        }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            REQUEST_CALL_PHONE -> {
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {

                }
                return
            }
            else -> super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    private var currentAnimation: Animator? = null

    private fun createPulseAnimation() {
        val callButton = findViewById<Button>(R.id.call)

        callBtnView.alpha = 1F

        currentAnimation = ObjectAnimator.ofFloat(callButton, "alpha", 0F).apply {
            duration = 300
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
        }

        currentAnimation?.addListener(onEnd = {
            callButton.alpha = 1F
        })
    }

    override fun onResume() {
        // Register a listener for the sensor.
        super.onResume()

        proximitySensor.startListening()
    }

    override fun onPause() {
        // Be sure to unregister the sensor when the activity pauses.
        super.onPause()
        proximitySensor.stopListening()
    }
}