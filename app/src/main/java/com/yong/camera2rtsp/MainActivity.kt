package com.yong.camera2rtsp

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {
    private var btnStart: Button? = null
    private var btnStop: Button? = null
    private var inputServerIp: EditText? = null
    private var inputServerPort: EditText? = null

    private var cameraEncoder: CameraEncoder? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        btnStart = findViewById(R.id.main_btn_start)
        btnStop = findViewById(R.id.main_btn_stop)
        inputServerIp = findViewById(R.id.main_input_server_ip)
        inputServerPort = findViewById(R.id.main_input_server_port)

        btnStart!!.setOnClickListener(btnListener)
        btnStop!!.setOnClickListener(btnListener)
    }

    private val btnListener = View.OnClickListener { view ->
        when(view.id) {
            R.id.main_btn_start -> {
                if(cameraEncoder == null) {
                    val serverIP =  inputServerIp!!.text.toString()
                    val serverPort = inputServerPort!!.text.toString().toInt()
                    cameraEncoder = CameraEncoder(serverIP, serverPort)
                    cameraEncoder?.startCameraCapture(applicationContext, 1280, 720, 2000000, 30)
                }
            }
            R.id.main_btn_stop -> {
                if(cameraEncoder != null) {
                    cameraEncoder?.stopCameraCapture()
                    cameraEncoder = null
                }
            }
        }
    }
}