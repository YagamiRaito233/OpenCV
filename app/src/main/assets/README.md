# Assets目录说明

## 需要添加的文件

请将OpenCV的Haar Cascade分类器文件复制到此目录：

1. **haarcascade_frontalface_default.xml** - 正面人脸检测分类器

### 如何获取文件

该文件位于OpenCV Android SDK的以下路径：
```
opencv/etc/haarcascades/haarcascade_frontalface_default.xml
```

### 复制方法

1. 找到OpenCV SDK中的 `opencv/etc/haarcascades/haarcascade_frontalface_default.xml` 文件
2. 将该文件复制到 `app/src/main/assets/` 目录下
3. 确保文件名完全一致：`haarcascade_frontalface_default.xml`

### 其他可选的分类器文件

如果需要更好的检测效果，也可以添加：
- `haarcascade_frontalface_alt.xml` - 替代正面人脸检测器
- `haarcascade_frontalface_alt2.xml` - 另一个替代版本

注意：如果使用其他分类器，需要修改 `FaceDetector.kt` 中的文件名。