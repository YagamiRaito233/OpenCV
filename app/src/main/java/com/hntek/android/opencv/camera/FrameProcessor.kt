package com.hntek.android.opencv.camera

import android.graphics.Bitmap
import android.util.Log
import androidx.annotation.WorkerThread
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc
import com.hntek.android.opencv.utils.FaceDetector
import com.hntek.android.opencv.utils.FaceMatcher
import com.hntek.android.opencv.utils.OpenCVHelper
import com.hntek.android.opencv.utils.FaceVerificationResult

/**
 * 帧处理器
 * 负责处理每一帧图像，进行人脸检测和比对
 */
class FrameProcessor(
    private val faceDetector: FaceDetector,
    private val faceMatcher: FaceMatcher
) {
    private val tag = "FrameProcessor"

    // 身份证照片的Mat（用于比对）
    private var idCardFaceMat: Mat? = null
    private var idCardProcessedFace: Mat? = null

    // 身份证照片中的人脸数量
    var idCardFaceCount: Int = 0
        private set

    // 身份证照片是否启用“中心取景框”(ROI)裁剪
    var idCardRoiEnabled: Boolean = true
        private set

    // 比对阈值（向后兼容，实际使用FaceMatcher中的多重条件验证）
    var similarityThreshold = 0.75

    // 取景框区域（用于限制检测范围）
    var viewportRect: android.graphics.Rect? = null
    var previewSize: android.util.Size? = null

    // 人脸质量过滤参数
    var minFaceSize: Int = 100  // 最小人脸尺寸（像素）
    var maxFaceSize: Int = 800  // 最大人脸尺寸（像素）

    // 回调接口
    var onFaceDetected: ((List<android.graphics.Rect>) -> Unit)? = null
    var onComparisonResult: ((Double) -> Unit)? = null  // 向后兼容
    var onVerificationResult: ((FaceVerificationResult) -> Unit)? = null  // 新的详细结果回调

    /**
     * 设置身份证照片用于比对
     * @param idCardBitmap 身份证照片的Bitmap
     */
    fun setIdCardImage(idCardBitmap: Bitmap) {
        try {
            Log.d(tag, "开始处理身份证照片，尺寸: ${idCardBitmap.width}x${idCardBitmap.height}")

            // 先尝试不裁剪直接检测（身份证照片通常人脸在中心，不需要裁剪）
            var faces = faceDetector.detectFaces(idCardBitmap, strictMode = true)
            var workingBitmap = idCardBitmap
            var usedRoi = false

            // 如果未检测到人脸，尝试使用ROI裁剪（如果启用）
            if (faces.isEmpty() && idCardRoiEnabled) {
                Log.d(tag, "未检测到人脸，尝试使用ROI裁剪")
                val roiBitmap = cropCenterSquare(idCardBitmap, 0.90f) // 增大裁剪比例到90%
                faces = faceDetector.detectFaces(roiBitmap, strictMode = true)
                workingBitmap = roiBitmap
                usedRoi = true
            }

            // 如果仍然未检测到，尝试非严格模式
            if (faces.isEmpty()) {
                Log.d(tag, "严格模式未检测到人脸，尝试非严格模式")
                faces = faceDetector.detectFaces(workingBitmap, strictMode = false)
            }

            idCardFaceCount = faces.size
            Log.d(tag, "检测到 ${faces.size} 个人脸 (使用ROI: $usedRoi)")

            if (faces.isEmpty()) {
                Log.w(tag, "身份证照片中未检测到人脸，请检查照片质量")
                idCardFaceMat = null
                idCardProcessedFace = null
                return
            }

            // 只有检测到1个人脸时才提取
            if (faces.size == 1) {
                // 提取第一个人脸
                val idCardMat = OpenCVHelper.bitmapToMat(workingBitmap)
                val faceRect = faces[0]
                Log.d(tag, "人脸位置: x=${faceRect.x}, y=${faceRect.y}, w=${faceRect.width}, h=${faceRect.height}")

                val extractedFace = faceDetector.extractFaceRegion(idCardMat, faceRect)

                if (extractedFace != null) {
                    // 预处理
                    idCardProcessedFace = faceDetector.preprocessFace(extractedFace)
                    Log.d(tag, "身份证人脸设置成功，检测到1个人脸")
                } else {
                    Log.w(tag, "无法提取身份证人脸")
                    idCardProcessedFace = null
                }

                idCardMat.release()
                extractedFace?.release()

                // 如果使用了ROI裁剪的临时bitmap，需要释放
                if (usedRoi && workingBitmap != idCardBitmap) {
                    workingBitmap.recycle()
                }
            } else {
                Log.w(tag, "身份证照片中检测到${faces.size}个人脸，需要恰好1个人脸")
                idCardProcessedFace = null

                // 如果使用了ROI裁剪的临时bitmap，需要释放
                if (usedRoi && workingBitmap != idCardBitmap) {
                    workingBitmap.recycle()
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "设置身份证照片失败: ${e.message}", e)
            idCardFaceCount = 0
            idCardProcessedFace = null
        }
    }

    /**
     * 裁剪图片中心正方形ROI（用于“上传照片取景框”）
     * @param cropScale 裁剪边长占短边比例，建议 0.75~0.9
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
     * 处理一帧图像
     * @param frameMat 当前帧的Mat
     * @return 处理后的Mat（带检测框）
     */
    @WorkerThread
    fun processFrame(frameMat: Mat): Mat {
        try {
            val resultMat = Mat()
            frameMat.copyTo(resultMat)

            // 检测人脸
            val allFaces = faceDetector.detectFaces(resultMat)
            var faces = allFaces

            // 如果设置了取景框，只保留取景框内的人脸
            if (viewportRect != null && previewSize != null) {
                faces = filterFacesInViewport(allFaces, viewportRect!!, previewSize!!, frameMat.width(), frameMat.height())
            }

            // 转换为人脸矩形列表（Android Rect格式，用于UI显示）
            val androidRects = faces.map { rect ->
                android.graphics.Rect(
                    rect.x,
                    rect.y,
                    rect.x + rect.width,
                    rect.y + rect.height
                )
            }

            // 通知UI更新
            onFaceDetected?.invoke(androidRects)

            // 只有在检测到1个人脸且身份证照片也有1个人脸时，才进行比对
            if (faces.size == 1 && idCardFaceCount == 1 && idCardProcessedFace != null) {
                val faceRect = faces[0]

                // 质量过滤：检查人脸尺寸是否合理
                if (isFaceQualityAcceptable(faceRect)) {
                    val extractedFace = faceDetector.extractFaceRegion(resultMat, faceRect)

                    if (extractedFace != null) {
                        val processedFace = faceDetector.preprocessFace(extractedFace)

                        // 使用新的多重条件验证方法
                        val verificationResult = faceMatcher.verifyFaces(processedFace, idCardProcessedFace!!)

                        // 向后兼容：通知旧的比对结果（使用加权分）
                        onComparisonResult?.invoke(verificationResult.confidence)

                        // 新的详细结果回调
                        onVerificationResult?.invoke(verificationResult)

                        // 在图像上绘制结果（明确使用FaceVerificationResult版本）
                        drawComparisonResultWithResult(resultMat, faceRect, verificationResult)

                        processedFace.release()
                        extractedFace.release()
                    }
                } else {
                    Log.d(tag, "人脸质量不符合要求，跳过比对")
                }
            }

            // 绘制人脸检测框
            drawFaceRectangles(resultMat, faces)

            return resultMat
        } catch (e: Exception) {
            Log.e(tag, "处理帧失败: ${e.message}", e)
            return frameMat
        }
    }

    /**
     * 绘制人脸检测框
     */
    private fun drawFaceRectangles(mat: Mat, faces: List<org.opencv.core.Rect>) {
        for (face in faces) {
            Imgproc.rectangle(
                mat,
                Point(face.x.toDouble(), face.y.toDouble()),
                Point((face.x + face.width).toDouble(), (face.y + face.height).toDouble()),
                Scalar(0.0, 255.0, 0.0),  // 绿色
                3
            )
        }
    }

    /**
     * 检查人脸质量是否可接受
     * @param faceRect 人脸矩形
     * @return 如果质量可接受返回true
     */
    private fun isFaceQualityAcceptable(faceRect: org.opencv.core.Rect): Boolean {
        val faceSize = maxOf(faceRect.width, faceRect.height)

        // 检查人脸尺寸是否在合理范围内
        if (faceSize < minFaceSize || faceSize > maxFaceSize) {
            Log.d(tag, "人脸尺寸不符合要求: $faceSize (要求: $minFaceSize-$maxFaceSize)")
            return false
        }

        // 检查宽高比是否合理（人脸通常是接近正方形的）
        val aspectRatio = faceRect.width.toDouble() / faceRect.height
        if (aspectRatio < 0.5 || aspectRatio > 2.0) {
            Log.d(tag, "人脸宽高比不合理: $aspectRatio")
            return false
        }

        return true
    }

    /**
     * 绘制比对结果（使用新的验证结果）
     */
    private fun drawComparisonResultWithResult(
        mat: Mat,
        faceRect: org.opencv.core.Rect,
        result: FaceVerificationResult
    ) {
        val color = if (result.isPass) {
            Scalar(0.0, 255.0, 0.0)  // 绿色：匹配
        } else {
            Scalar(0.0, 0.0, 255.0)  // 红色：不匹配
        }

        // 绘制边框
        Imgproc.rectangle(
            mat,
            Point(faceRect.x.toDouble(), faceRect.y.toDouble()),
            Point((faceRect.x + faceRect.width).toDouble(), (faceRect.y + faceRect.height).toDouble()),
            color,
            4
        )

        // 绘制相似度文本（显示加权分和通过状态）
        val statusText = if (result.isPass) "通过" else "未通过"
        val text = String.format("%s: %.1f%% (%d/%d)",
            statusText,
            result.confidence * 100,
            result.passedChecks,
            result.totalChecks
        )
        val textPosition = Point(faceRect.x.toDouble(), (faceRect.y - 10).toDouble())
        Imgproc.putText(
            mat,
            text,
            textPosition,
            Imgproc.FONT_HERSHEY_SIMPLEX,
            0.7,
            color,
            2
        )
    }

    /**
     * 绘制比对结果（向后兼容的旧方法）
     */
    private fun drawComparisonResult(
        mat: Mat,
        faceRect: org.opencv.core.Rect,
        similarity: Double
    ) {
        val isMatch = similarity >= similarityThreshold
        val color = if (isMatch) {
            Scalar(0.0, 255.0, 0.0)  // 绿色：匹配
        } else {
            Scalar(0.0, 0.0, 255.0)  // 红色：不匹配
        }

        // 绘制边框
        Imgproc.rectangle(
            mat,
            Point(faceRect.x.toDouble(), faceRect.y.toDouble()),
            Point((faceRect.x + faceRect.width).toDouble(), (faceRect.y + faceRect.height).toDouble()),
            color,
            4
        )

        // 绘制相似度文本
        val text = String.format("相似度: %.2f%%", similarity * 100)
        val textPosition = Point(faceRect.x.toDouble(), (faceRect.y - 10).toDouble())
        Imgproc.putText(
            mat,
            text,
            textPosition,
            Imgproc.FONT_HERSHEY_SIMPLEX,
            0.8,
            color,
            2
        )
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

        // 用“比例”做坐标映射（比直接 scaleX/scaleY 更稳，配合把分析帧旋转到正确方向后可大幅减少误过滤）
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

        // 第二轮：如果被过滤到 0（但确实检测到脸），放宽容差并“选最接近中心的那张”避免误报“未检测到人脸”
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
     * 释放资源
     */
    fun release() {
        idCardFaceMat?.release()
        idCardProcessedFace?.release()
        idCardFaceMat = null
        idCardProcessedFace = null
    }
}
