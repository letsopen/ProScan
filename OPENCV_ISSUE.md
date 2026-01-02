# OpenCV 依赖问题说明

## 问题描述

`org.opencv:opencv:4.9.0` 不包含 Android 子包（`org.opencv.android.*`），导致以下类无法使用：
- `org.opencv.android.BaseLoaderCallback`
- `org.opencv.android.LoaderCallbackInterface`
- `org.opencv.android.OpenCVLoader`
- `org.opencv.android.Utils`

## 当前解决方案

1. **简化实现**：`WeChatQRCodeScanner` 已简化为占位实现
2. **自动降级**：`isAvailable()` 始终返回 `false`，工厂类会自动跳过
3. **移除依赖**：OpenCV 依赖已注释，避免编译错误
4. **移除 Activity**：从 AndroidManifest 中移除了 `WeChatScanActivity`

## 影响

- ✅ **不影响其他功能**：其他扫码方案（华为、ML Kit、ZXing）正常工作
- ✅ **自动降级**：应用会自动使用可用的扫码方案
- ❌ **微信 OpenCV 功能不可用**：直到找到正确的 OpenCV Android 集成方式

## 未来解决方案

### 方案1：使用 OpenCV Manager（推荐）

使用 OpenCV Manager 动态加载 OpenCV 库：

```gradle
// 不需要添加 OpenCV 依赖
// 运行时通过 OpenCV Manager 加载
```

需要：
1. 用户安装 OpenCV Manager 应用
2. 或使用 OpenCV Manager API 下载

### 方案2：手动集成 OpenCV Android SDK

1. 从 OpenCV 官网下载 Android SDK
2. 手动集成到项目中
3. 使用静态库方式

### 方案3：使用其他 OpenCV Android 包

查找包含 Android 类的 OpenCV Maven 包，例如：
- `org.opencv:opencv-android`（如果存在）
- 或其他第三方提供的 OpenCV Android 包

## 当前可用方案

应用会自动使用以下扫码方案（按优先级）：
1. **华为 Scan Kit**（如果 HMS Core 可用）
2. **ML Kit 独立版本**（不需要 Google Play Services）
3. **ZXing**（备用方案）

所有方案都完全兼容 Android 10 (API 29)。

