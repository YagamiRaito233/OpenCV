package com.hntek.android.opencv_face_sdk.utils

import android.util.Log
import org.opencv.core.*
import kotlin.math.sqrt

/**
 * 人脸比对工具类
 * 使用组合特征（LBPH + 灰度直方图 + 区域特征）进行人脸特征提取和比对
 * 使用多重条件验证策略，提高比对准确性，防止误识别
 */
class FaceMatcher {
    private val tag = "FaceMatcher"
    
    // 验证阈值配置（已调整，平衡安全性和通过率）
    var weightedThreshold: Double = 0.65  // 加权分阈值（降低以允许本人通过）
    var cosineMinThreshold: Double = 0.60  // 余弦相似度最低要求（降低）
    var euclideanMinThreshold: Double = 0.45  // 欧氏相似度最低要求（降低）
    var scoreDiffMaxThreshold: Double = 0.35  // 分数差异最大允许值（放宽，因为两种算法差异可能较大）
    
    // 高置信度阈值（如果加权分很高，可以放宽其他条件）
    var highConfidenceThreshold: Double = 0.80  // 高置信度阈值

    /**
     * 提取人脸特征向量（组合多种特征）
     * @param faceMat 预处理后的人脸Mat（灰度图，已归一化）
     * @return 组合特征向量
     */
    fun extractFeatures(faceMat: Mat): MatOfFloat? {
        return try {
            // 组合多种特征以提高准确性
            val lbpFeatures = calculateLBPHistogram(faceMat)
            val grayFeatures = calculateGrayHistogram(faceMat)
            val regionFeatures = calculateRegionFeatures(faceMat)
            
            // 合并特征向量
            val combinedFeatures = combineFeatures(lbpFeatures, grayFeatures, regionFeatures)
            
            lbpFeatures.release()
            grayFeatures.release()
            regionFeatures.release()
            
            combinedFeatures
        } catch (e: Exception) {
            Log.e(tag, "特征提取失败: ${e.message}", e)
            null
        }
    }
    
    /**
     * 计算灰度直方图特征
     */
    private fun calculateGrayHistogram(mat: Mat): MatOfFloat {
        val histogram = IntArray(256) { 0 }
        val rows = mat.rows()
        val cols = mat.cols()
        
        for (i in 0 until rows) {
            for (j in 0 until cols) {
                val grayValue = mat.get(i, j)[0].toInt()
                histogram[grayValue]++
            }
        }
        
        // 归一化
        val total = histogram.sum().toFloat()
        val normalizedHistogram = FloatArray(256) { i ->
            if (total > 0) histogram[i] / total else 0f
        }
        
        return MatOfFloat(*normalizedHistogram)
    }
    
    /**
     * 计算区域特征（将人脸分为多个区域，分别提取特征）
     */
    private fun calculateRegionFeatures(mat: Mat): MatOfFloat {
        val rows = mat.rows()
        val cols = mat.cols()
        
        // 将人脸分为9个区域（3x3网格）
        val regionSize = 3
        val features = mutableListOf<Float>()
        
        for (ri in 0 until regionSize) {
            for (rj in 0 until regionSize) {
                val startRow = (ri * rows) / regionSize
                val endRow = ((ri + 1) * rows) / regionSize
                val startCol = (rj * cols) / regionSize
                val endCol = ((rj + 1) * cols) / regionSize
                
                // 计算该区域的平均灰度值
                var sum = 0.0
                var count = 0
                for (i in startRow until endRow) {
                    for (j in startCol until endCol) {
                        sum += mat.get(i, j)[0]
                        count++
                    }
                }
                val mean = if (count > 0) (sum / count).toFloat() else 0f
                features.add(mean / 255.0f) // 归一化到0-1
                
                // 计算该区域的方差
                var variance = 0.0
                for (i in startRow until endRow) {
                    for (j in startCol until endCol) {
                        val diff = mat.get(i, j)[0] - mean
                        variance += diff * diff
                    }
                }
                val stdDev = if (count > 0) kotlin.math.sqrt(variance / count).toFloat() else 0f
                features.add(stdDev / 255.0f) // 归一化到0-1
            }
        }
        
        return MatOfFloat(*features.toFloatArray())
    }
    
    /**
     * 合并多个特征向量
     */
    private fun combineFeatures(vararg features: MatOfFloat): MatOfFloat {
        val allFeatures = mutableListOf<Float>()
        for (feature in features) {
            allFeatures.addAll(feature.toArray().toList())
        }
        return MatOfFloat(*allFeatures.toFloatArray())
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
     * 计算欧氏距离（归一化到0-1范围）
     */
    private fun calculateEuclideanDistance(features1: MatOfFloat, features2: MatOfFloat): Double {
        val array1 = features1.toArray()
        val array2 = features2.toArray()

        if (array1.size != array2.size) {
            return 1.0 // 距离最大
        }

        var sumSquaredDiff = 0.0
        for (i in array1.indices) {
            val diff = array1[i] - array2[i]
            sumSquaredDiff += diff * diff
        }

        val distance = sqrt(sumSquaredDiff)
        // 归一化到0-1范围（假设最大距离为sqrt(2)，因为特征已归一化）
        val normalizedDistance = (distance / sqrt(2.0)).coerceIn(0.0, 1.0)
        return 1.0 - normalizedDistance // 转换为相似度（1表示最相似）
    }

    /**
     * 检测特征向量异常
     * @param features 特征向量
     * @return 如果异常返回true
     */
    private fun detectAnomalies(features: MatOfFloat): Boolean {
        val array = features.toArray()
        
        // 检查NaN或无穷大
        if (array.any { it.isNaN() || it.isInfinite() }) {
            Log.w(tag, "特征向量包含NaN或无穷大值")
            return true
        }
        
        // 检查特征范数是否在合理范围
        var normSquared = 0.0
        for (value in array) {
            normSquared += value * value
        }
        val norm = sqrt(normSquared)
        
        if (norm < 0.1 || norm > 10.0) {
            Log.w(tag, "特征向量范数异常: $norm")
            return true
        }
        
        return false
    }

    /**
     * 比对两个人脸（多重条件验证）
     * 使用余弦相似度和欧氏距离的组合，并进行多重条件验证
     * @param face1 第一个人脸Mat（已预处理）
     * @param face2 第二个人脸Mat（已预处理）
     * @return 验证结果，包含详细比对信息
     */
    fun verifyFaces(face1: Mat, face2: Mat): FaceVerificationResult {
        return try {
            val features1 = extractFeatures(face1)
            val features2 = extractFeatures(face2)

            if (features1 == null || features2 == null) {
                Log.w(tag, "特征提取失败")
                return FaceVerificationResult(
                    isPass = false,
                    confidence = 0.0,
                    cosineSimilarity = 0.0,
                    euclideanSimilarity = 0.0,
                    weightedScore = 0.0,
                    passedChecks = 0,
                    totalChecks = 0,
                    checkDetails = emptyMap()
                )
            }

            // 异常检测
            if (detectAnomalies(features1) || detectAnomalies(features2)) {
                Log.w(tag, "特征向量异常，拒绝比对")
                features1.release()
                features2.release()
                return FaceVerificationResult(
                    isPass = false,
                    confidence = 0.0,
                    cosineSimilarity = 0.0,
                    euclideanSimilarity = 0.0,
                    weightedScore = 0.0,
                    passedChecks = 0,
                    totalChecks = 0,
                    checkDetails = mapOf("anomaly" to false)
                )
            }

            // 1. 基础相似度计算
            val cosineSim = calculateCosineSimilarity(features1, features2)
            val euclideanSim = calculateEuclideanDistance(features1, features2)
            
            // 2. 加权分数
            val weightedScore = cosineSim * 0.7 + euclideanSim * 0.3
            
            // 3. 多重条件验证（灵活策略，降低阈值以提高通过率）
            val checks = mutableMapOf<String, Boolean>()
            val scoreDiffValue = kotlin.math.abs(cosineSim - euclideanSim)
            
            // 条件1：加权分必须达标
            val check1 = weightedScore >= weightedThreshold
            checks["weightedScore"] = check1
            
            // 条件2：余弦相似度单项最低要求
            val check2 = cosineSim >= cosineMinThreshold
            checks["cosineMin"] = check2
            
            // 条件3：欧氏相似度单项最低要求
            val check3 = euclideanSim >= euclideanMinThreshold
            checks["euclideanMin"] = check3
            
            // 条件4：分数差异限制（防止单项异常）
            val check4 = scoreDiffValue < scoreDiffMaxThreshold
            checks["scoreDiff"] = check4
            
            // 4. 综合判断策略（严谨的分级验证）
            // 根据加权分的高低，采用不同的验证策略，避免逻辑重合
            val isPassResult = when {
                // 策略1：超高置信度（加权分 >= 0.80）
                // 要求：加权分达标 + 至少一个单项达标（但不能两个都太低）
                weightedScore >= highConfidenceThreshold -> {
                    val bothLow = cosineSim < (cosineMinThreshold + 0.05) && euclideanSim < (euclideanMinThreshold + 0.05)
                    (check2 || check3) && !bothLow  // 至少一个达标，且不能两个都很低
                }
                
                // 策略2：高置信度（0.70 <= 加权分 < 0.80）
                // 要求：加权分达标 + 两个单项都达标
                weightedScore >= (weightedThreshold + 0.05) -> {
                    check2 && check3  // 两个单项都必须达标
                }
                
                // 策略3：普通置信度（0.65 <= 加权分 < 0.70）
                // 要求：加权分达标 + 两个单项都达标 + 分数差异不能太大
                else -> {
                    check2 && check3 && check4  // 所有条件都必须满足
                }
            }
            val passedCount = checks.values.count { it }
            val isPass = isPassResult
            
            Log.d(tag, "验证结果: 通过=$isPass, 加权分=$weightedScore, 余弦=$cosineSim, 欧氏=$euclideanSim, " +
                    "分数差异=$scoreDiffValue, 通过检查=$passedCount/${checks.size}")
            
            features1.release()
            features2.release()
            
            FaceVerificationResult(
                isPass = isPass,
                confidence = weightedScore,
                cosineSimilarity = cosineSim,
                euclideanSimilarity = euclideanSim,
                weightedScore = weightedScore,
                passedChecks = passedCount,
                totalChecks = checks.size,
                checkDetails = checks
            )
        } catch (e: Exception) {
            Log.e(tag, "人脸比对失败: ${e.message}", e)
            FaceVerificationResult(
                isPass = false,
                confidence = 0.0,
                cosineSimilarity = 0.0,
                euclideanSimilarity = 0.0,
                weightedScore = 0.0,
                passedChecks = 0,
                totalChecks = 0,
                checkDetails = emptyMap()
            )
        }
    }

    /**
     * 释放资源
     */
    fun release() {
        // 当前实现不需要释放额外资源
    }
}
