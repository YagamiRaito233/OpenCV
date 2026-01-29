package com.hntek.android.opencv.utils

/**
 * 人脸验证结果
 * 包含详细的比对信息和多重条件验证结果
 */
data class FaceVerificationResult(
    /**
     * 是否通过验证
     */
    val isPass: Boolean,
    
    /**
     * 置信度分数（加权分）
     */
    val confidence: Double,
    
    /**
     * 余弦相似度
     */
    val cosineSimilarity: Double,
    
    /**
     * 欧氏相似度
     */
    val euclideanSimilarity: Double,
    
    /**
     * 加权分数
     */
    val weightedScore: Double,
    
    /**
     * 通过的检查数量
     */
    val passedChecks: Int,
    
    /**
     * 总检查数量
     */
    val totalChecks: Int,
    
    /**
     * 检查详情（每个条件的通过状态）
     */
    val checkDetails: Map<String, Boolean>
)
