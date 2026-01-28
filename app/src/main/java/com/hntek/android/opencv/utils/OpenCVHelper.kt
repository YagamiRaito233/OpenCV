package com.hntek.android.opencv.utils

import android.content.Context
import android.util.Log
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat
import android.graphics.Bitmap

object OpenCVHelper {
    private const val TAG = "OpenCVHelper"
    private var isInitialized = false

    /**
     * 初始化OpenCV库
     * @return true表示初始化成功，false表示失败
     */
    fun init(context: Context): Boolean {
        if (isInitialized) {
            return true
        }

        return try {
            val result = OpenCVLoader.initLocal()
            if (result) {
                isInitialized = true
                Log.d(TAG, "OpenCV初始化成功")
            } else {
                Log.e(TAG, "OpenCV初始化失败")
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "OpenCV初始化异常: ${e.message}", e)
            false
        }
    }

    /**
     * 检查OpenCV是否已初始化
     */
    fun isOpenCVInitialized(): Boolean = isInitialized

    /**
     * 将Bitmap转换为OpenCV的Mat格式
     */
    fun bitmapToMat(bitmap: Bitmap): Mat {
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)
        return mat
    }

    /**
     * 将OpenCV的Mat格式转换为Bitmap
     */
    fun matToBitmap(mat: Mat): Bitmap {
        val bitmap = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(mat, bitmap)
        return bitmap
    }
}
