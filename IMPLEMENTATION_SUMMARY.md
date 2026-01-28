# 实时人脸和身份证照片比对系统 - 实现总结

## 已完成的功能

### 1. OpenCV集成 ✅
- 创建了 `OpenCVHelper` 工具类，负责OpenCV库的初始化
- 支持Bitmap和Mat格式之间的转换

### 2. 权限管理 ✅
- 添加了相机权限和存储权限到 `AndroidManifest.xml`
- 使用Accompanist Permissions库进行运行时权限请求
- 支持Android 13+的新权限模型

### 3. 人脸检测 ✅
- 创建了 `FaceDetector` 类，使用Haar Cascade分类器进行人脸检测
- 支持从assets目录加载分类器文件
- 实现了人脸区域提取和预处理功能
- 包含图像预处理：灰度化、直方图均衡化、归一化

### 4. 人脸特征提取和比对 ✅
- 创建了 `FaceMatcher` 类，实现LBPH（局部二值模式直方图）算法
- 支持欧氏距离和余弦相似度计算
- 实现了实时人脸与身份证照片的比对功能

### 5. 相机预览 ✅
- 使用CameraX实现实时相机预览
- 创建了 `CameraManager` 类管理相机生命周期
- 实现了图像帧的实时处理

### 6. 图像处理管道 ✅
- 创建了 `FrameProcessor` 类处理每一帧图像
- 实时检测人脸并绘制检测框
- 实时计算相似度并显示结果
- 支持设置比对阈值

### 7. 用户界面 ✅
- 使用Jetpack Compose构建现代化UI
- 实时显示相机预览
- 支持选择身份证照片
- 显示比对结果和相似度分数
- 显示检测到的人脸数量

## 项目结构

```
app/src/main/java/com/hntek/android/opencv/
├── MainActivity.kt                    # 主Activity，处理权限和界面
├── camera/
│   ├── CameraManager.kt              # 相机管理
│   └── FrameProcessor.kt             # 帧处理
├── ui/
│   └── FaceComparisonScreen.kt       # 主界面
└── utils/
    ├── OpenCVHelper.kt                # OpenCV工具类
    ├── FaceDetector.kt               # 人脸检测
    └── FaceMatcher.kt                # 人脸比对
```

## 核心依赖

- **OpenCV**: 4.12.0 (通过本地模块集成)
- **CameraX**: 1.3.0 (相机预览和图像分析)
- **Jetpack Compose**: 最新版本 (UI框架)
- **Accompanist Permissions**: 0.34.0 (权限处理)

## 使用说明

### 1. 确保Haar Cascade文件存在
Haar Cascade分类器文件已复制到 `app/src/main/assets/haarcascade_frontalface_default.xml`

### 2. 运行应用
1. 首次启动会请求相机和存储权限
2. 选择身份证照片（点击"选择身份证"按钮）
3. 将摄像头对准人脸，系统会自动检测并比对
4. 比对结果会实时显示在屏幕上

### 3. 比对阈值
默认相似度阈值为0.6（60%），可以在 `FrameProcessor` 中调整：
```kotlin
frameProcessor.similarityThreshold = 0.7  // 设置为70%
```

## 技术特点

1. **实时处理**: 使用CameraX的ImageAnalysis进行实时帧处理
2. **多线程**: 图像处理在后台线程执行，不阻塞UI
3. **资源管理**: 正确释放Mat对象，避免内存泄漏
4. **错误处理**: 完善的异常处理和日志记录

## 性能优化建议

1. **降低处理频率**: 可以每3-5帧处理一次，减少CPU占用
2. **降低分辨率**: 在保证检测精度的前提下降低处理分辨率
3. **人脸跟踪**: 检测到人脸后使用跟踪算法，减少重复检测
4. **GPU加速**: 可以考虑使用OpenCV的GPU模块加速处理

## 后续改进方向

1. 支持多人脸检测和比对
2. 添加人脸对齐功能，提高比对准确度
3. 支持保存比对记录
4. 添加更多特征提取算法（如深度学习模型）
5. 优化UI，添加更多交互功能

## 注意事项

1. 确保在真机上测试，模拟器可能无法使用相机
2. 光照条件会影响检测和比对效果
3. 人脸角度过大可能影响检测和比对准确性
4. 首次加载分类器文件可能需要一些时间
