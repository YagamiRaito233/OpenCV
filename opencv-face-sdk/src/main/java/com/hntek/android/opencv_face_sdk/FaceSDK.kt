package com.hntek.android.opencv_face_sdk

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.hntek.android.opencv_face_sdk.utils.*
import org.opencv.core.Mat
import org.opencv.core.Rect

/**
 * 人脸识别SDK主入口
 * 提供人脸检测和比对功能
 */
class FaceSDK private constructor(private val context: Context) {
    private val tag = "FaceSDK"
    
    private var faceDetector: FaceDetector? = null
    private var faceMatcher: FaceMatcher? = null
    private var isInitialized = false
    
    // 身份证照片的特征向量（预处理后的人脸Mat）
    private var idCardFaceMat: Mat? = null
    
    // 连续帧比对状态
    private val continuousFrameResults = mutableListOf<Boolean>()
    private val requiredPassFrames = 5  // 需要连续5帧通过
    
    // 取景框区域（用于限制检测范围）
    private var viewportRect: android.graphics.Rect? = null
    private var previewSize: android.util.Size? = null
    
    companion object {
        @Volatile
        private var instance: FaceSDK? = null
        
        /**
         * 初始化SDK
         * @param context 应用上下文
         * @return 初始化是否成功
         */
        fun init(context: Context): Boolean {
            return synchronized(this) {
                if (instance == null) {
                    instance = FaceSDK(context.applicationContext)
                }
                instance!!.initialize()
            }
        }
        
        /**
         * 获取SDK实例（必须先调用init）
         */
        fun getInstance(): FaceSDK {
            return instance ?: throw IllegalStateException("FaceSDK未初始化，请先调用init()")
        }
        
        /**
         * 释放SDK资源
         */
        fun release() {
            synchronized(this) {
                instance?.releaseInternal()
                instance = null
            }
        }
    }
    
    /**
     * 初始化SDK内部组件
     */
    private fun initialize(): Boolean {
        if (isInitialized) {
            return true
        }
        
        return try {
            // 初始化OpenCV
            if (!OpenCVHelper.init(context)) {
                Log.e(tag, "OpenCV初始化失败")
                return false
            }
            
            // 创建人脸检测器和比对器
            faceDetector = FaceDetector(context)
            faceMatcher = FaceMatcher()
            
            isInitialized = true
            Log.d(tag, "FaceSDK初始化成功")
            true
        } catch (e: Exception) {
            Log.e(tag, "FaceSDK初始化异常: ${e.message}", e)
            false
        }
    }
    
    /**
     * 设置身份证照片
     * 检测照片中的人脸并提取特征向量
     * @param idCardBitmap 身份证照片的Bitmap
     * @return 检测结果，包含是否检测到人脸、人脸数量等信息
     */
    fun setIdCardImage(idCardBitmap: Bitmap): IdCardDetectionResult {
        if (!isInitialized) {
            return IdCardDetectionResult(
                success = false,
                faceCount = 0,
                errorMessage = "SDK未初始化"
            )
        }
        
        return try {
            // 先尝试不裁剪直接检测（身份证照片通常人脸在中心，不需要裁剪）
            var faces = faceDetector!!.detectFaces(idCardBitmap, strictMode = true)
            var workingBitmap = idCardBitmap
            
            // 如果未检测到人脸，尝试使用ROI裁剪
            if (faces.isEmpty()) {
                Log.d(tag, "未检测到人脸，尝试使用ROI裁剪")
                val roiBitmap = cropCenterSquare(idCardBitmap, 0.90f)
                faces = faceDetector!!.detectFaces(roiBitmap, strictMode = true)
                workingBitmap = roiBitmap
            }
            
            // 如果仍然未检测到，尝试非严格模式
            if (faces.isEmpty()) {
                Log.d(tag, "严格模式未检测到人脸，尝试非严格模式")
                faces = faceDetector!!.detectFaces(workingBitmap, strictMode = false)
            }
            
            if (faces.isEmpty()) {
                // 释放之前的资源
                idCardFaceMat?.release()
                idCardFaceMat = null
                
                return IdCardDetectionResult(
                    success = false,
                    faceCount = 0,
                    errorMessage = "未检测到人脸"
                )
            }
            
            if (faces.size != 1) {
                // 释放之前的资源
                idCardFaceMat?.release()
                idCardFaceMat = null
                
                return IdCardDetectionResult(
                    success = false,
                    faceCount = faces.size,
                    errorMessage = "检测到${faces.size}个人脸，需要恰好1个人脸"
                )
            }
            
            // 提取人脸并预处理
            val idCardMat = OpenCVHelper.bitmapToMat(workingBitmap)
            val faceRect = faces[0]
            val extractedFace = faceDetector!!.extractFaceRegion(idCardMat, faceRect)
            
            if (extractedFace != null) {
                // 预处理人脸
                val processedFace = faceDetector!!.preprocessFace(extractedFace)
                
                // 释放旧的特征向量
                idCardFaceMat?.release()
                idCardFaceMat = processedFace
                
                // 重置连续帧比对状态
                continuousFrameResults.clear()
                
                idCardMat.release()
                extractedFace.release()
                
                if (workingBitmap != idCardBitmap) {
                    workingBitmap.recycle()
                }
                
                Log.d(tag, "身份证人脸设置成功，检测到1个人脸")
                IdCardDetectionResult(
                    success = true,
                    faceCount = 1,
                    errorMessage = null
                )
            } else {
                idCardMat.release()
                if (workingBitmap != idCardBitmap) {
                    workingBitmap.recycle()
                }
                
                IdCardDetectionResult(
                    success = false,
                    faceCount = faces.size,
                    errorMessage = "无法提取人脸区域"
                )
            }
        } catch (e: Exception) {
            Log.e(tag, "设置身份证照片失败: ${e.message}", e)
            IdCardDetectionResult(
                success = false,
                faceCount = 0,
                errorMessage = "处理异常: ${e.message}"
            )
        }
    }
    
    /**
     * 设置取景框区域（用于限制检测范围）
     * @param viewportRect UI上的取景框区域（外接矩形，像素坐标）
     * @param previewSize 预览View的实际尺寸
     */
    fun setViewport(viewportRect: android.graphics.Rect?, previewSize: android.util.Size?) {
        this.viewportRect = viewportRect
        this.previewSize = previewSize
    }
    
    /**
     * 处理实时摄像头画面帧
     * 检测帧中的人脸，提取特征向量，并与身份证照片进行比对
     * @param frameBitmap 实时画面帧的Bitmap
     * @return 比对结果，包含是否检测到人脸、比对是否通过等信息
     */
    fun processFrame(frameBitmap: Bitmap): FrameProcessResult {
        if (!isInitialized) {
            return FrameProcessResult(
                faceDetected = false,
                faceCount = 0,
                isMatch = false,
                verificationResult = null,
                errorMessage = "SDK未初始化"
            )
        }
        
        if (idCardFaceMat == null) {
            return FrameProcessResult(
                faceDetected = false,
                faceCount = 0,
                isMatch = false,
                verificationResult = null,
                errorMessage = "未设置身份证照片"
            )
        }
        
        return try {
            // 检测人脸
            val allFaces = faceDetector!!.detectFaces(frameBitmap, strictMode = false)
            
            // 如果设置了取景框，只保留取景框内的人脸
            val faces = if (viewportRect != null && previewSize != null) {
                filterFacesInViewport(allFaces, viewportRect!!, previewSize!!, frameBitmap.width, frameBitmap.height)
            } else {
                allFaces
            }
            
            if (faces.isEmpty()) {
                // 未检测到人脸，重置连续帧状态
                continuousFrameResults.clear()
                return FrameProcessResult(
                    faceDetected = false,
                    faceCount = 0,
                    isMatch = false,
                    verificationResult = null,
                    errorMessage = null
                )
            }
            
            if (faces.size != 1) {
                // 检测到多个人脸，重置连续帧状态
                continuousFrameResults.clear()
                return FrameProcessResult(
                    faceDetected = true,
                    faceCount = faces.size,
                    isMatch = false,
                    verificationResult = null,
                    errorMessage = "检测到${faces.size}个人脸，需要恰好1个人脸"
                )
            }
            
            // 提取人脸并预处理
            val frameMat = OpenCVHelper.bitmapToMat(frameBitmap)
            val faceRect = faces[0]
            val extractedFace = faceDetector!!.extractFaceRegion(frameMat, faceRect)
            
            if (extractedFace == null) {
                frameMat.release()
                continuousFrameResults.clear()
                return FrameProcessResult(
                    faceDetected = true,
                    faceCount = 1,
                    isMatch = false,
                    verificationResult = null,
                    errorMessage = "无法提取人脸区域"
                )
            }
            
            val processedFace = faceDetector!!.preprocessFace(extractedFace)
            
            // 进行比对
            val verificationResult = faceMatcher!!.verifyFaces(processedFace, idCardFaceMat!!)
            
            // 记录比对结果
            continuousFrameResults.add(verificationResult.isPass)
            
            // 保持最近N帧的结果（只保留最近requiredPassFrames帧）
            if (continuousFrameResults.size > requiredPassFrames) {
                continuousFrameResults.removeAt(0)
            }
            
            // 检查是否连续5帧都通过
            val isMatch = continuousFrameResults.size >= requiredPassFrames && 
                         continuousFrameResults.all { it }
            
            // 释放资源
            frameMat.release()
            extractedFace.release()
            processedFace.release()
            
            FrameProcessResult(
                faceDetected = true,
                faceCount = 1,
                isMatch = isMatch,
                verificationResult = verificationResult,
                errorMessage = null
            )
        } catch (e: Exception) {
            Log.e(tag, "处理帧失败: ${e.message}", e)
            continuousFrameResults.clear()
            FrameProcessResult(
                faceDetected = false,
                faceCount = 0,
                isMatch = false,
                verificationResult = null,
                errorMessage = "处理异常: ${e.message}"
            )
        }
    }
    
    /**
     * 裁剪图片中心正方形ROI
     */
    private fun cropCenterSquare(src: Bitmap, cropScale: Float): Bitmap {
        val safeScale = cropScale.coerceIn(0.4f, 1.0f)
        val w = src.width
        val h = src.height
        val side = (minOf(w, h) * safeScale).toInt().coerceAtLeast(1)
        val left = ((w - side) / 2).coerceAtLeast(0)
        val top = ((h - side) / 2).coerceAtLeast(0)
        return Bitmap.createBitmap(src, left, top, side.coerceAtMost(w - left), side.coerceAtMost(h - top))
    }
    
    /**
     * 过滤取景框内的人脸（圆形取景框）
     * @param faces 检测到的所有人脸
     * @param viewportRect UI上的取景框区域（外接矩形，像素坐标）
     * @param previewSize 预览View的实际尺寸
     * @param matWidth Mat图像的宽度
     * @param matHeight Mat图像的高度
     * @return 取景框内的人脸列表
     */
    private fun filterFacesInViewport(
        faces: List<org.opencv.core.Rect>,
        viewportRect: android.graphics.Rect,
        previewSize: android.util.Size,
        matWidth: Int,
        matHeight: Int
    ): List<org.opencv.core.Rect> {
        if (faces.isEmpty()) return faces

        // 用"比例"做坐标映射（比直接 scaleX/scaleY 更稳）
        val previewW = previewSize.width.toDouble().coerceAtLeast(1.0)
        val previewH = previewSize.height.toDouble().coerceAtLeast(1.0)
        val cxNorm = ((viewportRect.left + viewportRect.right) / 2.0) / previewW
        val cyNorm = ((viewportRect.top + viewportRect.bottom) / 2.0) / previewH
        val radiusNorm = (viewportRect.width() / 2.0) / minOf(previewW, previewH)

        val circleCenterX = cxNorm * matWidth
        val circleCenterY = cyNorm * matHeight
        val circleRadius = radiusNorm * minOf(matWidth.toDouble(), matHeight.toDouble())

        fun isInside(face: org.opencv.core.Rect, extraTolerance: Double): Boolean {
            val faceCenterX = face.x + face.width / 2.0
            val faceCenterY = face.y + face.height / 2.0
            val dx = faceCenterX - circleCenterX
            val dy = faceCenterY - circleCenterY
            val distance = kotlin.math.sqrt(dx * dx + dy * dy)
            val tol = maxOf(face.width, face.height) * extraTolerance
            return distance <= (circleRadius + tol)
        }

        // 第一轮：相对严格（容差 0.35）
        val filtered = faces.filter { isInside(it, 0.35) }
        if (filtered.isNotEmpty()) return filtered

        // 第二轮：如果被过滤到 0（但确实检测到脸），放宽容差并"选最接近中心的那张"
        val best = faces.minByOrNull { face ->
            val faceCenterX = face.x + face.width / 2.0
            val faceCenterY = face.y + face.height / 2.0
            val dx = faceCenterX - circleCenterX
            val dy = faceCenterY - circleCenterY
            dx * dx + dy * dy
        }

        return if (best != null && isInside(best, 0.7)) listOf(best) else emptyList()
    }
    
    /**
     * 重置连续帧比对状态
     */
    fun resetContinuousFrameState() {
        continuousFrameResults.clear()
    }
    
    /**
     * 获取当前连续通过的帧数
     */
    fun getContinuousPassFrames(): Int {
        return continuousFrameResults.count { it }
    }
    
    /**
     * 释放资源
     */
    private fun releaseInternal() {
        idCardFaceMat?.release()
        idCardFaceMat = null
        faceDetector = null
        faceMatcher = null
        continuousFrameResults.clear()
        isInitialized = false
    }
}

/**
 * 身份证照片检测结果
 */
data class IdCardDetectionResult(
    /**
     * 是否成功
     */
    val success: Boolean,
    
    /**
     * 检测到的人脸数量
     */
    val faceCount: Int,
    
    /**
     * 错误信息（如果失败）
     */
    val errorMessage: String?
)

/**
 * 帧处理结果
 */
data class FrameProcessResult(
    /**
     * 是否检测到人脸
     */
    val faceDetected: Boolean,
    
    /**
     * 检测到的人脸数量
     */
    val faceCount: Int,
    
    /**
     * 是否匹配（连续5帧都通过）
     */
    val isMatch: Boolean,
    
    /**
     * 当前帧的验证结果（详细比对信息）
     */
    val verificationResult: FaceVerificationResult?,
    
    /**
     * 错误信息（如果有）
     */
    val errorMessage: String?
)
