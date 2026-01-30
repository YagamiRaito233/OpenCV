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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.camera.view.PreviewView
import com.hntek.android.opencv.camera.CameraManager
import com.hntek.android.opencv_face_sdk.FaceSDK
import com.hntek.android.opencv_face_sdk.FrameProcessResult
import com.hntek.android.opencv_face_sdk.IdCardDetectionResult
import java.io.InputStream
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 人脸比对主界面
 * 作为调用方示例，使用FaceSDK进行人脸检测和比对
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FaceComparisonScreen() {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    
    // SDK初始化状态
    var sdkInitialized by remember { mutableStateOf(false) }
    
    // 身份证照片
    var idCardBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var idCardDetectionResult by remember { mutableStateOf<IdCardDetectionResult?>(null) }

    // 比对结果（用于稳定显示）
    var displayedSimilarity by remember { mutableStateOf<Double?>(null) }
    var isMatching by remember { mutableStateOf(false) }
    var verificationResult by remember { mutableStateOf<com.hntek.android.opencv_face_sdk.utils.FaceVerificationResult?>(null) }
    
    // 检测到的人脸数量（稳定显示，避免闪烁）
    var detectedFaceCount by remember { mutableStateOf(0) }
    var tempFaceCount by remember { mutableStateOf(0) }
    
    // 连续通过的帧数
    var continuousPassFrames by remember { mutableStateOf(0) }
    
    // 取景框区域（相对于预览View）
    var viewportRect by remember { mutableStateOf<android.graphics.Rect?>(null) }
    var previewSize by remember { mutableStateOf<android.util.Size?>(null) }
    
    // 预览View
    val previewView = remember { PreviewView(context) }
    
    // 相机管理器
    var cameraManager by remember { mutableStateOf<CameraManager?>(null) }
    
    // 初始化SDK
    LaunchedEffect(Unit) {
        sdkInitialized = FaceSDK.init(context)
        if (sdkInitialized) {
            cameraManager = CameraManager(context, previewView, lifecycleOwner)
            
            // 设置帧处理回调
            cameraManager?.onFrameProcessed = { bitmap ->
                // 调用SDK处理帧
                val result = FaceSDK.getInstance().processFrame(bitmap)
                
                // 更新UI状态
                tempFaceCount = if (result.faceDetected) result.faceCount else 0
                
                if (result.faceDetected && result.faceCount == 1) {
                    // 更新相似度显示
                    result.verificationResult?.let { vr ->
                        if (vr.confidence.isFinite() && vr.confidence >= 0.0) {
                            displayedSimilarity = if (displayedSimilarity == null) {
                                vr.confidence
                            } else {
                                displayedSimilarity!! * 0.7 + vr.confidence * 0.3
                            }
                            isMatching = result.isMatch
                            verificationResult = vr
                        }
                    }
                    
                    // 更新连续通过的帧数
                    continuousPassFrames = FaceSDK.getInstance().getContinuousPassFrames()
                } else {
                    // 未检测到人脸或检测到多个人脸，延迟清空显示
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                        delay(600)
                        if (tempFaceCount == 0 || tempFaceCount != 1) {
                            displayedSimilarity = null
                            isMatching = false
                            continuousPassFrames = 0
                        }
                    }
                }
            }
            
            // 启动相机
            cameraManager?.startCamera()
        }
    }
    
    // 使用防抖机制稳定更新人脸数量显示
    LaunchedEffect(tempFaceCount) {
        delay(200) // 200ms防抖延迟
        detectedFaceCount = tempFaceCount
    }
    
    // 取景框尺寸会在布局完成后才有值；这里随时同步到SDK，避免一直用null/旧值
    LaunchedEffect(viewportRect, previewSize) {
        FaceSDK.getInstance().setViewport(viewportRect, previewSize)
    }

    // 清理资源
    DisposableEffect(Unit) {
        onDispose {
            cameraManager?.stopCamera()
            FaceSDK.release()
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
                
                // 调用SDK设置身份证照片
                val result = FaceSDK.getInstance().setIdCardImage(bitmap)
                idCardDetectionResult = result
                
                if (!result.success) {
                    // 显示错误信息
                    android.util.Log.e("FaceComparisonScreen", "设置身份证照片失败: ${result.errorMessage}")
                }
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
        
        if (!sdkInitialized) {
            // SDK初始化中
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator()
                    Text("正在初始化SDK...")
                }
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
                    
                    // 显示比对结果覆盖层
                    val idCardFaceCountForUi = idCardDetectionResult?.faceCount ?: 0
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
                                // 显示连续通过的帧数
                                Text(
                                    text = "连续通过: $continuousPassFrames/5",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.8f)
                                )
                                // 显示验证详情
                                verificationResult?.let { result ->
                                    Text(
                                        text = "检查: ${result.passedChecks}/${result.totalChecks}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.8f)
                                    )
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
                
                // 状态信息
                Text(
                    text = if (detectedFaceCount > 0) {
                        "检测到 $detectedFaceCount 个人脸"
                    } else {
                        "未检测到人脸"
                    },
                    color = if (detectedFaceCount > 0) Color.White else Color.Gray,
                    style = MaterialTheme.typography.bodyMedium
                )
                
                // 显示身份证照片检测结果
                idCardDetectionResult?.let { result ->
                    Text(
                        text = if (result.success) {
                            "身份证照片：检测到 ${result.faceCount} 个人脸 ✓"
                        } else {
                            "身份证照片：${result.errorMessage ?: "检测失败"}"
                        },
                        color = if (result.success) Color(0xFF4CAF50) else Color(0xFFFF9800),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
