package com.hntek.android.opencv.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * 相机管理器
 * 负责管理CameraX的初始化和图像分析
 */
class CameraManager(
    private val context: Context,
    private val previewView: PreviewView,
    private val lifecycleOwner: LifecycleOwner
) {
    private val tag = "CameraManager"
    private var imageAnalysis: ImageAnalysis? = null
    private var camera: Camera? = null
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    
    var onImageCaptured: ((Bitmap) -> Unit)? = null
    var onFrameProcessed: ((Bitmap) -> Unit)? = null

    /**
     * 启动相机预览
     */
    fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            try {
                val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

                // 预览用例
                val preview = Preview.Builder()
                    .build()
                    .also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                // 图像分析用例（用于实时处理）
                imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor) { imageProxy ->
                            processImageProxy(imageProxy)
                        }
                    }

                // 解绑所有用例
                cameraProvider.unbindAll()

                // 尝试选择摄像头，按优先级：前置 -> 后置
                val cameraSelectors = listOf(
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                )

                var cameraBound = false
                for (cameraSelector in cameraSelectors) {
                    try {
                        // 绑定用例到相机
                        camera = cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageAnalysis
                        )
                        Log.d(tag, "相机启动成功，使用选择器: ${cameraSelector}")
                        cameraBound = true
                        break
                    } catch (exc: Exception) {
                        Log.w(tag, "尝试使用选择器失败: ${cameraSelector}, 错误: ${exc.message}")
                    }
                }

                if (!cameraBound) {
                    Log.e(tag, "所有相机选择器都失败，无法启动相机")
                }

            } catch (exc: Exception) {
                Log.e(tag, "初始化相机失败", exc)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /**
     * 处理图像帧
     */
    private fun processImageProxy(imageProxy: ImageProxy) {
        try {
            // 将ImageProxy转换为Bitmap
            val bitmap = imageProxyToBitmap(imageProxy)

            // 旋转到与PreviewView一致的方向
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            val rotatedBitmap = if (rotationDegrees != 0) {
                rotateBitmap(bitmap, rotationDegrees)
            } else {
                bitmap
            }
            
            // 通知外部处理
            onFrameProcessed?.invoke(rotatedBitmap)
            
            // 如果需要保存图像
            onImageCaptured?.invoke(rotatedBitmap)
            
        } catch (e: Exception) {
            Log.e(tag, "处理图像失败: ${e.message}", e)
        } finally {
            imageProxy.close()
        }
    }

    /**
     * 将ImageProxy转换为Bitmap
     */
    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap {
        val yBuffer = imageProxy.planes[0].buffer
        val uBuffer = imageProxy.planes[1].buffer
        val vBuffer = imageProxy.planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = android.graphics.YuvImage(
            nv21,
            android.graphics.ImageFormat.NV21,
            imageProxy.width,
            imageProxy.height,
            null
        )

        val out = java.io.ByteArrayOutputStream()
        try {
            yuvImage.compressToJpeg(
                android.graphics.Rect(0, 0, imageProxy.width, imageProxy.height),
                90,
                out
            )
            val imageBytes = out.toByteArray()
            return android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        } finally {
            out.close()
        }
    }

    private fun rotateBitmap(src: Bitmap, degrees: Int): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
    }

    /**
     * 停止相机
     */
    fun stopCamera() {
        cameraExecutor.shutdown()
        imageAnalysis?.clearAnalyzer()
    }
}
