package com.yong.camera2rtsp

import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class CameraEncoder(serverIp: String, private val serverPort: Int) {
    private var backgroundHandler: Handler? = null
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var inputSurface: Surface? = null
    private var mediaCodec: MediaCodec? = null

    private var destinationAddress: InetAddress = InetAddress.getByName(serverIp)
    private var udpSocket: DatagramSocket = DatagramSocket()

    fun startCameraCapture(context: Context, width: Int, height: Int, bitRate: Int, frameRate: Int) {
        val handlerThread = HandlerThread("CameraBackground").also { it.start() }
        backgroundHandler = Handler(handlerThread.looper)

        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            val cameraId = cameraManager.cameraIdList[0]
            initializeMediaCodec(width, height, bitRate, frameRate)

            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    createCameraCaptureSession()
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    cameraDevice = null
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                    cameraDevice = null
                }
            }, backgroundHandler)
        } catch(e: CameraAccessException) {
            e.printStackTrace()
        } catch(e: SecurityException) {
            e.printStackTrace()
        }
    }

    fun stopCameraCapture() {
        try {
            captureSession?.apply {
                stopRepeating()
                abortCaptures()
                close()
            }
            captureSession = null
            cameraDevice?.close()
            cameraDevice = null
            mediaCodec?.apply {
                stop()
                release()
            }
            mediaCodec = null
            if(!udpSocket.isClosed) {
                udpSocket.close()
            }
        } catch(e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    private fun initializeMediaCodec(width: Int, height: Int, bitRate: Int, frameRate: Int) {
        try {
            mediaCodec = MediaCodec.createEncoderByType("video/avc").apply {
                val format = MediaFormat.createVideoFormat("video/avc", width, height).apply {
                    setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                    setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
                    setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
                    setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1) // I-Frame 간격
                }
                configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                inputSurface = createInputSurface()
                start()
            }

            Thread { handleEncodedData() }.start()
        } catch(e: IOException) {
            e.printStackTrace()
        }
    }

    private fun createCameraCaptureSession() {
        val surfaces = listOf(inputSurface)
        val captureRequestBuilder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)?.apply {
            addTarget(inputSurface!!)
        }

        cameraDevice?.createCaptureSession(surfaces, object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) {
                captureSession = session
                try {
                    captureRequestBuilder?.build()?.let {
                        captureSession?.setRepeatingRequest(it, null, backgroundHandler)
                    }
                } catch(e: CameraAccessException) {
                    e.printStackTrace()
                }
            }

            override fun onConfigureFailed(session: CameraCaptureSession) {
                Log.e(LOG_TAG, "Camera capture session configuration failed.")
            }
        }, backgroundHandler)
    }

    private fun handleEncodedData() {
        val bufferInfo = MediaCodec.BufferInfo()
        while(true) {
            val outputBufferId = mediaCodec?.dequeueOutputBuffer(bufferInfo, 10000) ?: -1
            if(outputBufferId >= 0) {
                val encodedData = mediaCodec?.getOutputBuffer(outputBufferId)

                encodedData?.let {
                    if(bufferInfo.size > 0) {
                        val data = ByteArray(bufferInfo.size)
                        it.get(data)
                        sendUdpPacket(data)

                        mediaCodec?.releaseOutputBuffer(outputBufferId, false)
                    }
                }
            } else if(outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                Log.d(LOG_TAG, "Output format changed: ${mediaCodec?.outputFormat}")
            } else if(outputBufferId == MediaCodec.INFO_TRY_AGAIN_LATER) {
                Log.d(LOG_TAG, "No output from encoder available, try again later.")
            }
        }
    }

    private fun sendUdpPacket(data: ByteArray) {
        try {
            val packet = DatagramPacket(data, data.size, destinationAddress, serverPort)
            udpSocket.send(packet)
        } catch(e: IOException) {
            e.printStackTrace()
        }
    }

    companion object {
        private const val LOG_TAG = "CameraEncoder"
    }
}