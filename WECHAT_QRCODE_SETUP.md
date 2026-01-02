# 微信 OpenCV 二维码识别设置指南

## 概述

微信 OpenCV 二维码识别基于微信团队开源的二维码识别引擎，已集成到 OpenCV 的 `wechat_qrcode` 模块中。该方案使用 CNN 模型进行二维码检测和超分辨率处理，具有高准确率和强大的识别能力。

## 模型文件下载

使用微信 OpenCV 二维码识别需要以下4个模型文件：

1. `detect.prototxt` - 检测器模型配置文件
2. `detect.caffemodel` - 检测器模型文件
3. `sr.prototxt` - 超分辨率模型配置文件
4. `sr.caffemodel` - 超分辨率模型文件

### 下载方式

#### 方式1：从 OpenCV 官方仓库下载

1. 访问 OpenCV 官方仓库：
   https://github.com/opencv/opencv_contrib/tree/master/modules/wechat_qrcode/models

2. 下载以下文件：
   - `detect.prototxt`
   - `detect.caffemodel`
   - `sr.prototxt`
   - `sr.caffemodel`

#### 方式2：从 OpenCV 官方下载页面

访问 OpenCV 官方下载页面，找到 wechat_qrcode 模块的模型文件。

## 安装步骤

### 1. 创建 assets 目录

如果项目中没有 `app/src/main/assets/` 目录，请创建它。

### 2. 复制模型文件

将下载的4个模型文件复制到 `app/src/main/assets/` 目录下：

```
app/src/main/assets/
├── detect.prototxt
├── detect.caffemodel
├── sr.prototxt
└── sr.caffemodel
```

### 3. 验证文件

确保文件已正确放置，应用会在运行时自动检查这些文件是否存在。

## 使用说明

### 自动选择

应用会自动检测模型文件是否存在，如果存在则优先使用微信 OpenCV 二维码识别。

### 手动选择

```java
BarcodeScanner scanner = BarcodeScannerFactory.createScanner(
    BarcodeScannerFactory.ScannerType.WECHAT_QRCODE
);
```

## 特性

- ✅ **高准确率**：微信官方开源，识别准确率极高
- ✅ **强大识别能力**：支持模糊、倾斜、远距离二维码识别
- ✅ **超分辨率处理**：使用 CNN 模型进行超分辨率处理，提升识别效果
- ✅ **完全离线**：不依赖任何网络服务
- ⚠️ **首次加载**：需要初始化 OpenCV 和加载模型，首次使用可能稍慢

## 注意事项

1. **模型文件大小**：4个模型文件总共约几MB，会增加应用体积
2. **OpenCV 初始化**：首次使用需要初始化 OpenCV，可能需要几秒钟
3. **内存使用**：模型加载会占用一定内存
4. **文件路径**：模型文件必须放在 `assets` 目录下，应用会自动复制到文件目录

## 故障排除

### 问题：模型文件未找到

**解决方案**：
1. 检查文件是否在 `app/src/main/assets/` 目录下
2. 检查文件名是否正确（区分大小写）
3. 清理并重新构建项目

### 问题：OpenCV 初始化失败

**解决方案**：
1. 确保已添加 OpenCV 依赖：`implementation 'org.opencv:opencv-android:4.8.0'`
2. 检查设备是否支持 OpenCV
3. 查看日志获取详细错误信息

### 问题：识别速度慢

**原因**：首次使用需要加载模型，后续使用会更快。

**解决方案**：
- 这是正常现象，模型加载后识别速度会提升
- 可以考虑在应用启动时预加载模型

## 参考资源

- [OpenCV wechat_qrcode 模块](https://github.com/opencv/opencv_contrib/tree/master/modules/wechat_qrcode)
- [OpenCV 官方文档](https://docs.opencv.org/)
- [微信二维码引擎原理](https://github.com/opencv/opencv_contrib/tree/master/modules/wechat_qrcode)

