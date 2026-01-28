package com.hntek.android.opencv.ui

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.hntek.android.opencv.utils.FaceDetector
import kotlin.math.min

/**
 * 身份证照片取景页面
 * 允许用户缩放和拖拽照片，将人脸对齐到取景框中心
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdCardCropScreen(
    originalBitmap: Bitmap,
    faceDetector: FaceDetector?,
    onConfirm: (Bitmap) -> Unit,
    onCancel: () -> Unit
) {
    val density = LocalDensity.current
    
    // 照片缩放和偏移状态
    var imageScale by remember { mutableStateOf(1f) }
    var imageOffsetX by remember { mutableStateOf(0f) }
    var imageOffsetY by remember { mutableStateOf(0f) }
    
    // 检测到的人脸位置（用于显示）
    var detectedFaces by remember { mutableStateOf<List<android.graphics.Rect>>(emptyList()) }
    
    // 容器尺寸
    var containerWidth by remember { mutableStateOf(0f) }
    var containerHeight by remember { mutableStateOf(0f) }
    
    // 初始化时检测人脸
    LaunchedEffect(originalBitmap) {
        if (faceDetector != null) {
            val faces = faceDetector.detectFaces(originalBitmap, strictMode = false)
            detectedFaces = faces.map { rect ->
                android.graphics.Rect(
                    rect.x,
                    rect.y,
                    rect.x + rect.width,
                    rect.y + rect.height
                )
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
            title = { Text("调整身份证照片") },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            navigationIcon = {
                IconButton(onClick = onCancel) {
                    Icon(
                        imageVector = Icons.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        )
        
        // 提示文字
        Text(
            text = "缩放和拖动照片，将人脸对齐到取景框中心",
            modifier = Modifier.padding(16.dp),
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium
        )
        
        // 照片预览区域（可缩放）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(16.dp)
                .background(Color.Gray.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                .onGloballyPositioned { coordinates ->
                    containerWidth = coordinates.size.width.toFloat()
                    containerHeight = coordinates.size.height.toFloat()
                },
            contentAlignment = Alignment.Center
        ) {
            val transformState = rememberTransformableState { zoomChange, offsetChange, _ ->
                imageScale = (imageScale * zoomChange).coerceIn(0.5f, 3f)
                imageOffsetX += offsetChange.x
                imageOffsetY += offsetChange.y
            }
            
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .transformable(state = transformState)
            ) {
                // 使用Image组件显示图片，配合graphicsLayer实现缩放和偏移
                Image(
                    bitmap = originalBitmap.asImageBitmap(),
                    contentDescription = "身份证照片",
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = imageScale
                            scaleY = imageScale
                            translationX = imageOffsetX
                            translationY = imageOffsetY
                        },
                    contentScale = ContentScale.Fit
                )
                
                // 绘制覆盖层（人脸框和取景框）
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val bitmapWidth = originalBitmap.width.toFloat()
                    val bitmapHeight = originalBitmap.height.toFloat()
                    
                    // 计算图片实际显示尺寸
                    val baseScale = minOf(
                        containerWidth / bitmapWidth,
                        containerHeight / bitmapHeight
                    )
                    val displayWidth = bitmapWidth * baseScale * imageScale
                    val displayHeight = bitmapHeight * baseScale * imageScale
                    
                    val offsetX = (containerWidth - displayWidth) / 2f + imageOffsetX
                    val offsetY = (containerHeight - displayHeight) / 2f + imageOffsetY
                    
                    // 绘制检测到的人脸框
                    detectedFaces.forEach { faceRect ->
                        val faceX = offsetX + (faceRect.left.toFloat() / bitmapWidth) * displayWidth
                        val faceY = offsetY + (faceRect.top.toFloat() / bitmapHeight) * displayHeight
                        val faceW = (faceRect.width().toFloat() / bitmapWidth) * displayWidth
                        val faceH = (faceRect.height().toFloat() / bitmapHeight) * displayHeight
                        
                        drawRect(
                            color = Color.Yellow.copy(alpha = 0.6f),
                            topLeft = Offset(faceX, faceY),
                            size = Size(faceW, faceH),
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }
                    
                    // 绘制取景框（圆形）
                    val viewportDiameter = minOf(containerWidth, containerHeight) * 0.80f
                    val viewportRadius = viewportDiameter / 2f
                    val viewportCenterX = containerWidth / 2f
                    val viewportCenterY = containerHeight / 2f
                    
                    drawCircle(
                        color = Color.White.copy(alpha = 0.8f),
                        center = Offset(viewportCenterX, viewportCenterY),
                        radius = viewportRadius,
                        style = Stroke(width = 3.dp.toPx())
                    )
                    
                    // 绘制十字辅助线
                    val guideLength = viewportRadius * 0.3f
                    drawLine(
                        color = Color.White.copy(alpha = 0.4f),
                        start = Offset(viewportCenterX - guideLength, viewportCenterY),
                        end = Offset(viewportCenterX + guideLength, viewportCenterY),
                        strokeWidth = 1.dp.toPx()
                    )
                    drawLine(
                        color = Color.White.copy(alpha = 0.4f),
                        start = Offset(viewportCenterX, viewportCenterY - guideLength),
                        end = Offset(viewportCenterX, viewportCenterY + guideLength),
                        strokeWidth = 1.dp.toPx()
                    )
                }
            }
        }
        
        // 操作按钮
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(
                onClick = {
                    imageScale = 1f
                    imageOffsetX = 0f
                    imageOffsetY = 0f
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Text("重置")
            }
            
            Button(
                onClick = {
                    // 根据当前缩放和偏移状态，裁剪取景框区域
                    val croppedBitmap = cropViewportRegion(
                        originalBitmap,
                        imageScale,
                        imageOffsetX,
                        imageOffsetY,
                        containerWidth,
                        containerHeight
                    )
                    if (croppedBitmap != null) {
                        onConfirm(croppedBitmap)
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("确认")
            }
        }
    }
}

/**
 * 根据当前缩放和偏移状态，裁剪取景框区域
 */
private fun cropViewportRegion(
    originalBitmap: Bitmap,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    containerWidth: Float,
    containerHeight: Float
): Bitmap? {
    return try {
        val bitmapWidth = originalBitmap.width.toFloat()
        val bitmapHeight = originalBitmap.height.toFloat()
        
        // 计算图片在容器中的显示尺寸
        val baseScale = minOf(
            containerWidth / bitmapWidth,
            containerHeight / bitmapHeight
        )
        val displayWidth = bitmapWidth * baseScale * scale
        val displayHeight = bitmapHeight * baseScale * scale
        
        // 计算图片在容器中的偏移
        val imageOffsetX = (containerWidth - displayWidth) / 2f + offsetX
        val imageOffsetY = (containerHeight - displayHeight) / 2f + offsetY
        
        // 计算取景框在容器中的位置（圆形，直径80%）
        val viewportDiameter = minOf(containerWidth, containerHeight) * 0.80f
        val viewportCenterX = containerWidth / 2f
        val viewportCenterY = containerHeight / 2f
        val viewportRadius = viewportDiameter / 2f
        
        // 计算取景框在原始图片上的位置
        // 取景框中心在容器中的坐标
        val viewportCenterInImageX = viewportCenterX - imageOffsetX
        val viewportCenterInImageY = viewportCenterY - imageOffsetY
        
        // 转换为原始图片坐标
        val viewportCenterInOriginalX = (viewportCenterInImageX / displayWidth) * bitmapWidth
        val viewportCenterInOriginalY = (viewportCenterInImageY / displayHeight) * bitmapHeight
        
        // 计算取景框半径在原始图片中的大小
        val viewportRadiusInOriginal = (viewportRadius / displayWidth) * bitmapWidth
        
        // 计算裁剪区域（正方形，边长为直径）
        val cropSize = (viewportRadiusInOriginal * 2).toInt()
        val cropX = (viewportCenterInOriginalX - viewportRadiusInOriginal).toInt().coerceAtLeast(0)
        val cropY = (viewportCenterInOriginalY - viewportRadiusInOriginal).toInt().coerceAtLeast(0)
        val finalCropX = cropX.coerceAtMost(bitmapWidth.toInt() - cropSize)
        val finalCropY = cropY.coerceAtMost(bitmapHeight.toInt() - cropSize)
        val finalCropSize = cropSize.coerceAtMost(bitmapWidth.toInt() - finalCropX)
            .coerceAtMost(bitmapHeight.toInt() - finalCropY)
        
        if (finalCropSize > 0 && finalCropX >= 0 && finalCropY >= 0) {
            Bitmap.createBitmap(
                originalBitmap,
                finalCropX,
                finalCropY,
                finalCropSize,
                finalCropSize
            )
        } else {
            Log.e("IdCardCropScreen", "裁剪区域无效: cropX=$finalCropX, cropY=$finalCropY, size=$finalCropSize")
            null
        }
    } catch (e: Exception) {
        Log.e("IdCardCropScreen", "裁剪失败: ${e.message}", e)
        null
    }
}
