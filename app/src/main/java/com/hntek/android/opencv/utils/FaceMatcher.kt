package com.hntek.android.opencv.utils

import android.util.Log
import org.opencv.core.*
import kotlin.math.sqrt

/**
 * 人脸比对工具类
 * 使用LBPH (Local Binary Patterns Histograms) 算法进行人脸特征提取和比对
 * 注意：OpenCV 4.12.0不包含face模块，因此我们手动实现LBP特征提取
 * 使用余弦相似度进行人脸比对
 */
class FaceMatcher {
    private val tag = "FaceMatcher"

    /**
     * 提取人脸特征向量（使用LBPH直方图）
     * @param faceMat 预处理后的人脸Mat（灰度图，已归一化）
     * @return 特征向量（直方图）
     */
    fun extractFeatures(faceMat: Mat): MatOfFloat? {
        return try {
            // 使用LBPH算法提取特征
            // 这里我们手动计算LBP直方图作为特征
            val histogram = calculateLBPHistogram(faceMat)
            histogram
        } catch (e: Exception) {
            Log.e(tag, "特征提取失败: ${e.message}", e)
            null
        }
    }

    /**
     * 计算LBP直方图特征
     */
    private fun calculateLBPHistogram(mat: Mat): MatOfFloat {
        val rows = mat.rows()
        val cols = mat.cols()
        val histogram = IntArray(256) { 0 }

        // 计算LBP值
        for (i in 1 until rows - 1) {
            for (j in 1 until cols - 1) {
                val center = mat.get(i, j)[0].toInt()
                var lbpValue = 0

                // 8邻域LBP编码
                val neighbors = arrayOf(
                    mat.get(i - 1, j - 1)[0].toInt(),  // 左上
                    mat.get(i - 1, j)[0].toInt(),      // 上
                    mat.get(i - 1, j + 1)[0].toInt(),  // 右上
                    mat.get(i, j + 1)[0].toInt(),      // 右
                    mat.get(i + 1, j + 1)[0].toInt(),  // 右下
                    mat.get(i + 1, j)[0].toInt(),       // 下
                    mat.get(i + 1, j - 1)[0].toInt(),  // 左下
                    mat.get(i, j - 1)[0].toInt()       // 左
                )

                for (k in neighbors.indices) {
                    if (neighbors[k] >= center) {
                        lbpValue = lbpValue or (1 shl (7 - k))
                    }
                }

                histogram[lbpValue]++
            }
        }

        // 归一化直方图
        val total = histogram.sum().toFloat()
        val normalizedHistogram = FloatArray(256) { i ->
            if (total > 0) histogram[i] / total else 0f
        }

        return MatOfFloat(*normalizedHistogram)
    }

    /**
     * 计算余弦相似度
     * @param features1 第一个特征向量
     * @param features2 第二个特征向量
     * @return 余弦相似度，值越大表示越相似（范围0-1）
     */
    private fun calculateCosineSimilarity(features1: MatOfFloat, features2: MatOfFloat): Double {
        val array1 = features1.toArray()
        val array2 = features2.toArray()

        if (array1.size != array2.size) {
            return 0.0
        }

        var dotProduct = 0.0
        var norm1 = 0.0
        var norm2 = 0.0

        for (i in array1.indices) {
            dotProduct += array1[i] * array2[i]
            norm1 += array1[i] * array1[i]
            norm2 += array2[i] * array2[i]
        }

        val denominator = sqrt(norm1) * sqrt(norm2)
        return if (denominator > 0) {
            dotProduct / denominator
        } else {
            0.0
        }
    }

    /**
     * 比对两个人脸
     * 使用余弦相似度进行比对
     * @param face1 第一个人脸Mat（已预处理）
     * @param face2 第二个人脸Mat（已预处理）
     * @return 相似度分数（0-1），1表示完全相同，0表示完全不同
     */
    fun compareFaces(face1: Mat, face2: Mat): Double {
        return try {
            val features1 = extractFeatures(face1)
            val features2 = extractFeatures(face2)

            if (features1 == null || features2 == null) {
                Log.w(tag, "特征提取失败")
                return 0.0
            }

            // 使用余弦相似度
            val similarity = calculateCosineSimilarity(features1, features2)
            
            Log.d(tag, "余弦相似度: $similarity")
            
            features1.release()
            features2.release()
            
            similarity
        } catch (e: Exception) {
            Log.e(tag, "人脸比对失败: ${e.message}", e)
            0.0
        }
    }

    /**
     * 释放资源
     */
    fun release() {
        // 当前实现不需要释放额外资源
    }
}
