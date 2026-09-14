package com.xjyzs.qrscanner

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.ImageReader
import android.net.Uri
import android.os.*
import android.util.Size
import android.view.Surface
import android.view.TextureView
import android.widget.FrameLayout
import com.king.wechat.qrcode.WeChatQRCodeDetector
import kotlinx.coroutines.*
import org.opencv.OpenCV
import org.opencv.core.CvType
import org.opencv.core.Mat
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : Activity() {

    private lateinit var cameraManager: CameraManager
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private lateinit var imageReader: ImageReader

    private val cameraThread = HandlerThread("ScanEngineThread", Process.THREAD_PRIORITY_URGENT_DISPLAY).apply { start() }
    private val cameraHandler = Handler(cameraThread.looper)

    @Volatile private var isEngineReady = false
    private val isScanned = AtomicBoolean(false)

    private lateinit var textureView: TextureView
    private lateinit var rootLayout: FrameLayout
    private val streamSize = Size(1280, 720)
    private var sensorOrientation = 90

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CoroutineScope(Dispatchers.IO).launch {
            OpenCV.initOpenCV()
            WeChatQRCodeDetector.init(this@MainActivity)
            isEngineReady = true
        }

        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        sensorOrientation = try {
            cameraManager.getCameraCharacteristics("0")
                .get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90
        } catch (e: Exception) { 90 }

        rootLayout = FrameLayout(this).apply {
            setBackgroundColor(android.graphics.Color.BLACK)
        }
        textureView = TextureView(this).apply {
            surfaceTextureListener = textureListener
        }
        rootLayout.addView(
            textureView,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        )
        setContentView(rootLayout)

        initImageReader(streamSize.width, streamSize.height)
    }

    override fun onResume() {
        super.onResume()
        if (textureView.isAvailable) {
            configureTransform(textureView.width, textureView.height)
            openCamera()
        } else {
            textureView.surfaceTextureListener = textureListener
        }
    }

    override fun onPause() {
        closeCamera()
        super.onPause()
    }

    private fun closeCamera() {
        try {
            captureSession?.close()
            captureSession = null
            cameraDevice?.close()
            cameraDevice = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private val textureListener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
            surface.setDefaultBufferSize(streamSize.width, streamSize.height)
            configureTransform(width, height)
            openCamera()
        }
        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
            configureTransform(width, height)
        }
        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
            closeCamera()
            return true
        }
        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
    }

    private fun configureTransform(viewWidth: Int, viewHeight: Int) {
        val matrix = Matrix()
        val viewRect = RectF(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat())
        val rotated = sensorOrientation == 90 || sensorOrientation == 270
        val bufferW = if (rotated) streamSize.height else streamSize.width
        val bufferH = if (rotated) streamSize.width else streamSize.height

        val bufferRect = RectF(0f, 0f, bufferW.toFloat(), bufferH.toFloat())
        val centerX = viewRect.centerX()
        val centerY = viewRect.centerY()

        bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY())
        matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL)
        val scale = maxOf(
            viewHeight.toFloat() / bufferH.toFloat(),
            viewWidth.toFloat() / bufferW.toFloat()
        )
        matrix.postScale(scale, scale, centerX, centerY)
        matrix.postRotate((sensorOrientation - 90).toFloat(), centerX, centerY)

        textureView.setTransform(matrix)
    }

    private var cachedYBytes: ByteArray? = null

    private fun initImageReader(width: Int, height: Int) {
        imageReader = ImageReader.newInstance(width, height, ImageFormat.YUV_420_888, 2)
        imageReader.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
            if (isScanned.get() || !isEngineReady) {
                image.close()
                return@setOnImageAvailableListener
            }

            val plane = image.planes[0]
            val buffer = plane.buffer
            val remaining = buffer.remaining()
            if (cachedYBytes == null || cachedYBytes!!.size != remaining) {
                cachedYBytes = ByteArray(remaining)
            }
            val bytes = cachedYBytes!!
            buffer.get(bytes)
            val yMat = Mat(image.height, image.width, CvType.CV_8UC1)
            yMat.put(0, 0, bytes)

            val results = WeChatQRCodeDetector.detectAndDecode(yMat)
            yMat.release()
            image.close()

            if (results.isNotEmpty() && results[0].isNotEmpty()) {
                if (isScanned.compareAndSet(false, true)) {
                    onScanSuccess(results[0])
                }
            }
        }, cameraHandler)
    }

    @SuppressLint("MissingPermission")
    private fun openCamera() {
        if (cameraDevice != null) return
        try {
            cameraManager.openCamera("0", object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    startCaptureSession()
                }
                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    cameraDevice = null
                }
                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                    cameraDevice = null
                }
            }, cameraHandler)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startCaptureSession() {
        val camera = cameraDevice ?: return
        val surfaceTexture = textureView.surfaceTexture ?: return
        surfaceTexture.setDefaultBufferSize(streamSize.width, streamSize.height)
        val previewSurface = Surface(surfaceTexture)
        val readerSurface = imageReader.surface

        camera.createCaptureSession(listOf(previewSurface, readerSurface), object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) {
                if (cameraDevice == null) return
                captureSession = session
                try {
                    val requestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                        addTarget(previewSurface)
                        addTarget(readerSurface)
                        set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                    }
                    session.setRepeatingRequest(requestBuilder.build(), null, cameraHandler)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            override fun onConfigureFailed(session: CameraCaptureSession) {}
        }, cameraHandler)
    }

    private fun onScanSuccess(url: String) {
        runOnUiThread {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            startActivity(intent)
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        closeCamera()
        imageReader.close()
        cameraThread.quitSafely()
    }
}