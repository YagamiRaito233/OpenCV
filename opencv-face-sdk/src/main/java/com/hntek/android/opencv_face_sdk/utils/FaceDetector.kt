package com.hntek.android.opencv_face_sdk.utils

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import org.opencv.objdetect.CascadeClassifier
import java.io.File
import java.io.FileOutputStream

/**
 * 人脸检测工具类
 * 使用OpenCV的Haar Cascade分类器进行人脸检测
 */
class FaceDetector(private val context: Context) {
    private var faceClassifier: CascadeClassifier? = null
    private val tag = "FaceDetector"

    init {
        loadCascadeClassifier()
    }

    /**
     * 加载Haar Cascade分类器
     */
    private fun loadCascadeClassifier() {
        try {
            // 从assets目录加载
            try {
                val inputStream = context.assets.open("haarcascade_frontalface_default.xml")
                val cascadeDir = context.getDir("cascade", Context.MODE_PRIVATE)
                val cascadeFile = File(cascadeDir, "haarcascade_frontalface_default.xml")

                val outputStream = FileOutputStream(cascadeFile)
                val buffer = ByteArray(4096)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                }
                inputStream.close()
                outputStream.close()

                faceClassifier = CascadeClassifier(cascadeFile.absolutePath)
                if (faceClassifier?.empty() == false) {
                    Log.d(tag, "从assets加载人脸检测分类器成功")
                    return
                }
            } catch (e: Exception) {
                Log.d(tag, "从assets加载失败，尝试其他方法: ${e.message}")
            }

        } catch (e: Exception) {
            Log.e(tag, "加载分类器失败: ${e.message}", e)
        }
    }

    /**
     * 从Bitmap中检测人脸
     * @param bitmap 输入的Bitmap图像
     * @param strictMode 严格模式，用于身份证照片等场景，减少误检
     * @return 检测到的人脸矩形列表，如果没有检测到则返回空列表
     */
    fun detectFaces(bitmap: Bitmap, strictMode: Boolean = false): List<Rect> {
        if (faceClassifier == null) {
            Log.w(tag, "人脸分类器未初始化")
            return emptyList()
        }

        try {
            // 将Bitmap转换为Mat
            val mat = Mat()
            Utils.bitmapToMat(bitmap, mat)

            // 转换为灰度图
            val grayMat = Mat()
            if (mat.channels() == 3) {
                Imgproc.cvtColor(mat, grayMat, Imgproc.COLOR_RGB2GRAY)
            } else if (mat.channels() == 4) {
                Imgproc.cvtColor(mat, grayMat, Imgproc.COLOR_RGBA2GRAY)
            } else {
                mat.copyTo(grayMat)
            }

            // 直方图均衡化，提高检测效果
            val equalizedMat = Mat()
            Imgproc.equalizeHist(grayMat, equalizedMat)

            // 根据模式调整检测参数
            val minNeighbors = if (strictMode) 6 else 3  // 严格模式需要更多邻居确认
            val minSize = if (strictMode) {
                // 身份证照片中，人脸通常至少占图像宽度的15%
                val minFaceSize = (minOf(bitmap.width, bitmap.height) * 0.15).toDouble()
                Size(minFaceSize, minFaceSize)
            } else {
                Size(30.0, 30.0)
            }

            val maxSize = if (strictMode) {
                // 身份证照片中，人脸通常不超过图像宽度的60%
                val maxFaceSize = (minOf(bitmap.width, bitmap.height) * 0.6).toDouble()
                Size(maxFaceSize, maxFaceSize)
            } else {
                Size()
            }

            // 检测人脸
            val faces = MatOfRect()
            faceClassifier?.detectMultiScale(
                equalizedMat,
                faces,
                1.15,  // scaleFactor: 增大步长，减少检测次数，提高精度
                minNeighbors,
                0,     // flags: 旧版OpenCV的标识，现在不使用
                minSize,
                maxSize
            )

            var faceList = faces.toList()

            // 严格模式：过滤不合理的人脸检测结果
            if (strictMode && faceList.isNotEmpty()) {
                faceList = filterValidFaces(faceList, bitmap.width, bitmap.height)
            }

            // 释放资源
            mat.release()
            grayMat.release()
            equalizedMat.release()
            faces.release()

            Log.d(tag, "检测到 ${faceList.size} 个人脸 (严格模式: $strictMode)")
            return faceList
        } catch (e: Exception) {
            Log.e(tag, "人脸检测失败: ${e.message}", e)
            return emptyList()
        }
    }

    /**
     * 过滤有效的人脸检测结果
     * 身份证照片中的人脸通常：
     * 1. 宽高比接近1:1（0.7-1.3之间）
     * 2. 位于图像中心区域
     * 3. 尺寸较大（排除小的误检）
     */
    private fun filterValidFaces(faces: List<Rect>, imageWidth: Int, imageHeight: Int): List<Rect> {
        if (faces.isEmpty()) return emptyList()

        val filteredFaces = mutableListOf<Rect>()
        val centerX = imageWidth / 2.0
        val centerY = imageHeight / 2.0
        val imageArea = imageWidth * imageHeight

        for (face in faces) {
            // 1. 检查宽高比（人脸通常是接近正方形的）
            val aspectRatio = face.width.toDouble() / face.height
            if (aspectRatio < 0.6 || aspectRatio > 1.5) {
                Log.d(tag, "过滤：宽高比不合理 $aspectRatio")
                continue
            }

            // 2. 检查人脸尺寸（至少占图像面积的2%）
            val faceArea = face.width * face.height
            val areaRatio = faceArea.toDouble() / imageArea
            if (areaRatio < 0.02) {
                Log.d(tag, "过滤：人脸太小 ${areaRatio * 100}%")
                continue
            }

            // 3. 检查人脸位置（人脸中心应该在图像中心区域，允许偏移30%）
            val faceCenterX = face.x + face.width / 2.0
            val faceCenterY = face.y + face.height / 2.0
            val offsetX = kotlin.math.abs(faceCenterX - centerX) / imageWidth
            val offsetY = kotlin.math.abs(faceCenterY - centerY) / imageHeight

            // 对于身份证照片，允许人脸稍微偏离中心，但不要太远
            if (offsetX > 0.4 || offsetY > 0.4) {
                Log.d(tag, "过滤：位置偏离中心 offsetX=$offsetX, offsetY=$offsetY")
                continue
            }

            filteredFaces.add(face)
        }

        // 4. 如果过滤后还有多个，选择最大的那个（通常是真正的人脸）
        if (filteredFaces.size > 1) {
            val largestFace = filteredFaces.maxByOrNull { it.width * it.height }
            Log.d(tag, "多个候选人脸，选择最大的: ${largestFace?.width}x${largestFace?.height}")
            return listOfNotNull(largestFace)
        }

        return filteredFaces
    }

    /**
     * 从Mat中检测人脸
     * @param mat 输入的Mat图像
     * @param strictMode 严格模式，用于身份证照片等场景，减少误检
     * @return 检测到的人脸矩形列表
     */
    fun detectFaces(mat: Mat, strictMode: Boolean = false): List<Rect> {
        if (faceClassifier == null) {
            Log.w(tag, "人脸分类器未初始化")
            return emptyList()
        }

        try {
            // 转换为灰度图
            val grayMat = Mat()
            if (mat.channels() == 3) {
                Imgproc.cvtColor(mat, grayMat, Imgproc.COLOR_RGB2GRAY)
            } else if (mat.channels() == 4) {
                Imgproc.cvtColor(mat, grayMat, Imgproc.COLOR_RGBA2GRAY)
            } else {
                mat.copyTo(grayMat)
            }

            // 直方图均衡化
            val equalizedMat = Mat()
            Imgproc.equalizeHist(grayMat, equalizedMat)

            // 根据模式调整检测参数
            val minNeighbors = if (strictMode) 6 else 3
            val minSize = if (strictMode) {
                val minFaceSize = (minOf(mat.width(), mat.height()) * 0.15).toDouble()
                Size(minFaceSize, minFaceSize)
            } else {
                Size(30.0, 30.0)
            }

            val maxSize = if (strictMode) {
                val maxFaceSize = (minOf(mat.width(), mat.height()) * 0.6).toDouble()
                Size(maxFaceSize, maxFaceSize)
            } else {
                Size()
            }

            // 检测人脸
            val faces = MatOfRect()
            faceClassifier?.detectMultiScale(
                equalizedMat,
                faces,
                1.15,
                minNeighbors,
                0,
                minSize,
                maxSize
            )

            var faceList = faces.toList()

            // 严格模式：过滤不合理的人脸检测结果
            if (strictMode && faceList.isNotEmpty()) {
                faceList = filterValidFaces(faceList, mat.width(), mat.height())
            }

            // 释放资源
            grayMat.release()
            equalizedMat.release()
            faces.release()

            return faceList
        } catch (e: Exception) {
            Log.e(tag, "人脸检测失败: ${e.message}", e)
            return emptyList()
        }
    }

    /**
     * 提取人脸区域
     * @param mat 原始图像Mat
     * @param faceRect 人脸矩形区域
     * @return 提取的人脸Mat，如果失败返回null
     */
    fun extractFaceRegion(mat: Mat, faceRect: Rect): Mat? {
        return try {
            val faceMat = Mat(mat, faceRect)
            val result = Mat()
            faceMat.copyTo(result)
            faceMat.release()
            result
        } catch (e: Exception) {
            Log.e(tag, "提取人脸区域失败: ${e.message}", e)
            null
        }
    }

    /**
     * 预处理人脸图像（归一化、调整大小）
     * @param faceMat 人脸Mat
     * @param targetSize 目标尺寸
     * @return 预处理后的人脸Mat
     */
    fun preprocessFace(faceMat: Mat, targetSize: Size = Size(100.0, 100.0)): Mat {
        val resized = Mat()
        Imgproc.resize(faceMat, resized, targetSize)

        // 转换为灰度图（如果还不是）
        val gray = Mat()
        if (resized.channels() == 3) {
            Imgproc.cvtColor(resized, gray, Imgproc.COLOR_RGB2GRAY)
        } else if (resized.channels() == 4) {
            Imgproc.cvtColor(resized, gray, Imgproc.COLOR_RGBA2GRAY)
        } else {
            resized.copyTo(gray)
        }

        // 直方图均衡化
        val equalized = Mat()
        Imgproc.equalizeHist(gray, equalized)

        resized.release()
        gray.release()

        return equalized
    }
}
