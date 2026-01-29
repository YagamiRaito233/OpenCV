package com.hntek.android.opencv.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.Canvas
import androidx.camera.view.PreviewView
import com.hntek.android.opencv.camera.CameraManager
import com.hntek.android.opencv.camera.FrameProcessor
import com.hntek.android.opencv.utils.FaceDetector
import com.hntek.android.opencv.utils.FaceMatcher
import com.hntek.android.opencv.utils.OpenCVHelper
import com.hntek.android.opencv.utils.FaceVerificationResult
import java.io.InputStream
import kotlinx.coroutines.delay

/**
 * 人脸比对主界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FaceComparisonScreen() {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    
    // OpenCV初始化状态
    var openCVInitialized by remember { mutableStateOf(false) }
    
    // 身份证照片
    var idCardBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // 比对结果（用于稳定显示）
    var displayedSimilarity by remember { mutableStateOf<Double?>(null) }
    var isMatching by remember { mutableStateOf(false) }
    var verificationResult by remember { mutableStateOf<FaceVerificationResult?>(null) }
    
    // 检测到的人脸数量（稳定显示，避免闪烁）
    var detectedFaceCount by remember { mutableStateOf(0) }
    
    // 用于防抖的临时状态
    var tempFaceCount by remember { mutableStateOf(0) }
    
    // 取景框区域（相对于预览View）
    var viewportRect by remember { mutableStateOf<android.graphics.Rect?>(null) }
    var previewSize by remember { mutableStateOf<android.util.Size?>(null) }
    
    // 预览View
    val previewView = remember { PreviewView(context) }
    
    // 工具类实例（延迟创建，等待OpenCV初始化完成）
    var faceDetector by remember { mutableStateOf<FaceDetector?>(null) }
    var faceMatcher by remember { mutableStateOf<FaceMatcher?>(null) }
    
    // 相机管理器和帧处理器
    var cameraManager by remember { mutableStateOf<CameraManager?>(null) }
    var frameProcessor by remember { mutableStateOf<FrameProcessor?>(null) }
    
    // 初始化OpenCV
    LaunchedEffect(Unit) {
        openCVInitialized = OpenCVHelper.init(context)
        if (openCVInitialized) {
            // OpenCV初始化完成后，再创建依赖OpenCV的工具类
            faceDetector = FaceDetector(context)
            faceMatcher = FaceMatcher()
            
            cameraManager = CameraManager(context, previewView, lifecycleOwner)
            frameProcessor = FrameProcessor(faceDetector!!, faceMatcher!!)
            
            // 设置帧处理回调：使用新的多重条件验证结果
            frameProcessor?.onVerificationResult = { result ->
                if (result.confidence.isFinite() && result.confidence >= 0.0) {
                    // 轻量平滑（EMA），让数值更稳定
                    displayedSimilarity = if (displayedSimilarity == null) {
                        result.confidence
                    } else {
                        displayedSimilarity!! * 0.7 + result.confidence * 0.3
                    }
                    // 使用多重条件验证的结果
                    isMatching = result.isPass
                    verificationResult = result
                }
            }
            
            // 向后兼容：保留旧的回调
            frameProcessor?.onComparisonResult = { similarity ->
                if (similarity.isFinite() && similarity >= 0.0) {
                    // 轻量平滑（EMA），让数值更稳定
                    displayedSimilarity = if (displayedSimilarity == null) {
                        similarity
                    } else {
                        displayedSimilarity!! * 0.7 + similarity * 0.3
                    }
                    // 使用FrameProcessor中的阈值进行判断（向后兼容）
                    val threshold = frameProcessor?.similarityThreshold ?: 0.75
                    isMatching = (displayedSimilarity ?: similarity) >= threshold
                }
            }
            
            frameProcessor?.onFaceDetected = { faces ->
                // 更新临时人脸数量
                tempFaceCount = faces.size
            }
            
            // 启动相机
            cameraManager?.onFrameProcessed = { mat ->
                val processedMat = frameProcessor?.processFrame(mat)
                processedMat?.release()
            }
            
            cameraManager?.startCamera()
        }
    }

    // 取景框尺寸会在布局完成后才有值；这里随时同步到 FrameProcessor，避免一直用 null / 旧值
    LaunchedEffect(frameProcessor, viewportRect, previewSize) {
        frameProcessor?.viewportRect = viewportRect
        frameProcessor?.previewSize = previewSize
    }
    
    // 使用防抖机制稳定更新人脸数量显示
    LaunchedEffect(tempFaceCount) {
        delay(200) // 200ms防抖延迟
        detectedFaceCount = tempFaceCount
    }

    // 相似度显示也做“延迟清空”，短暂丢帧不闪烁
    LaunchedEffect(detectedFaceCount, frameProcessor?.idCardFaceCount) {
        val idCount = frameProcessor?.idCardFaceCount ?: 0
        if (detectedFaceCount == 0 || idCount != 1) {
            delay(600) // 600ms 保护时间
            val idCount2 = frameProcessor?.idCardFaceCount ?: 0
            if (detectedFaceCount == 0 || idCount2 != 1) {
                displayedSimilarity = null
                isMatching = false
            }
        }
    }
    
    // 清理资源
    DisposableEffect(Unit) {
        onDispose {
            cameraManager?.stopCamera()
            frameProcessor?.release()
            faceMatcher?.release()
        }
    }
    
    // 图片选择器
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val inputStream: InputStream? = context.contentResolver.openInputStream(it)
                val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                
                idCardBitmap = bitmap
                frameProcessor?.setIdCardImage(bitmap)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 标题栏
        TopAppBar(
            title = { Text("人脸比对系统") },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        )
        
        if (!openCVInitialized) {
            // OpenCV初始化中
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text("正在初始化OpenCV...")
            }
        } else {
            // 主内容区域
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 相机预览区域
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color.Black, RoundedCornerShape(8.dp))
                ) {
                    AndroidView(
                        factory = { previewView },
                        modifier = Modifier
                            .fillMaxSize()
                            .onGloballyPositioned { coordinates ->
                                // 获取预览区域的实际尺寸
                                val width = coordinates.size.width
                                val height = coordinates.size.height
                                
                                // 计算圆形取景框（居中，直径占屏幕宽度的70%）
                                val diameter = minOf(width, height) * 0.7f
                                val radius = diameter / 2f
                                val centerX = width / 2f
                                val centerY = height / 2f
                                
                                // 计算圆形外接矩形（用于坐标转换和检测）
                                val frameX = centerX - radius
                                val frameY = centerY - radius
                                
                                viewportRect = android.graphics.Rect(
                                    frameX.toInt(),
                                    frameY.toInt(),
                                    (frameX + diameter).toInt(),
                                    (frameY + diameter).toInt()
                                )
                                
                                // 获取相机预览的实际分辨率
                                previewSize = android.util.Size(
                                    previewView.width,
                                    previewView.height
                                )
                            }
                    )
                    
                    // 绘制圆形人脸取景框
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        viewportRect?.let { rect ->
                            val strokePx = 3.dp.toPx()
                            
                            // 计算圆形中心点和半径
                            val centerX = (rect.left + rect.right) / 2f
                            val centerY = (rect.top + rect.bottom) / 2f
                            val radius = minOf(rect.width(), rect.height()) / 2f
                            
                            // 绘制圆形取景框边框（白色，带透明度）
                            drawCircle(
                                color = Color.White.copy(alpha = 0.8f),
                                center = Offset(centerX, centerY),
                                radius = radius,
                                style = Stroke(width = strokePx)
                            )
                            
                            // 绘制圆形内部的辅助线（可选，帮助用户对准）
                            val guideLineLength = radius * 0.3f
                            
                            // 水平辅助线
                            drawLine(
                                color = Color.White.copy(alpha = 0.4f),
                                start = Offset(centerX - guideLineLength, centerY),
                                end = Offset(centerX + guideLineLength, centerY),
                                strokeWidth = 1.dp.toPx()
                            )
                            
                            // 垂直辅助线
                            drawLine(
                                color = Color.White.copy(alpha = 0.4f),
                                start = Offset(centerX, centerY - guideLineLength),
                                end = Offset(centerX, centerY + guideLineLength),
                                strokeWidth = 1.dp.toPx()
                            )
                        }
                    }
                    
                    // 显示比对结果覆盖层：取景框内有人脸时持续显示（有值则显示百分比）
                    val idCardFaceCountForUi = frameProcessor?.idCardFaceCount ?: 0
                    if (detectedFaceCount > 0 && idCardFaceCountForUi == 1) {
                        Card(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isMatching) {
                                    Color(0xFF4CAF50).copy(alpha = 0.9f)
                                } else {
                                    Color(0xFFF44336).copy(alpha = 0.9f)
                                }
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = if (isMatching) "匹配" else "不匹配",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = Color.White
                                )
                                Text(
                                    text = displayedSimilarity?.let { String.format("%.1f%%", it * 100) } ?: "计算中…",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = Color.White
                                )
                                // 显示验证详情（如果有多重条件验证结果）
                                verificationResult?.let { result ->
                                    Text(
                                        text = "检查: ${result.passedChecks}/${result.totalChecks}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.8f)
                                    )
                                    if (!result.isPass && result.passedChecks < result.totalChecks) {
                                        Text(
                                            text = "未通过条件验证",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.White.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                
                // 身份证照片显示区域
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 身份证照片预览
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(120.dp)
                            .background(Color.Gray.copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (idCardBitmap != null) {
                            Image(
                                bitmap = idCardBitmap!!.asImageBitmap(),
                                contentDescription = "身份证照片",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Text("未选择身份证照片", color = Color.Gray)
                        }
                    }

                    // 选择身份证照片按钮
                    Button(
                        onClick = { imagePickerLauncher.launch("image/*") },
                        modifier = Modifier.height(56.dp)
                    ) {
                        Text("选择身份证")
                    }
                }
                
                // 状态信息（持续显示，不闪烁）
                Text(
                    text = if (detectedFaceCount > 0) {
                        "检测到 $detectedFaceCount 个人脸"
                    } else {
                        "未检测到人脸"
                    },
                    color = if (detectedFaceCount > 0) Color.White else Color.Gray,
                    style = MaterialTheme.typography.bodyMedium
                )
                
                // 显示身份证照片人脸数量
                if (idCardBitmap != null) {
                    val idCardFaceCount = frameProcessor?.idCardFaceCount ?: 0
                    Text(
                        text = if (idCardFaceCount == 1) {
                            "身份证照片：检测到 1 个人脸"
                        } else if (idCardFaceCount > 1) {
                            "身份证照片：检测到 $idCardFaceCount 个人脸（需要1个）"
                        } else {
                            "身份证照片：未检测到人脸"
                        },
                        color = if (idCardFaceCount == 1) Color(0xFF4CAF50) else Color(0xFFFF9800),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
