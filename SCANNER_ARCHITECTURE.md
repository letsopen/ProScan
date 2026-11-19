# 扫码架构说明

## 概述

本项目采用可插拔的扫码架构，支持多种扫码实现。当前支持（按降级优先级排序）：

1. **华为 HMS Core Scan Kit**（华为设备优先方案）
2. **ML Kit Barcode Scanning 独立版本**（默认引擎，不需要 Google Play Services）
3. **ZXing-CPP**（原生 C++ 实现，全码制，性能优秀）

> Google ML Kit Code Scanner（`play-services-code-scanner`，依赖 Google Play Services）已于 v2.9 移除。

## 架构设计

### 核心接口

#### `BarcodeScanner`
扫码接口，定义了统一的扫码方法：
- `startScan(Activity, ScanCallback)`: 启动扫码
- `getName()`: 获取实现名称（同时作为主界面标题的展示标签）
- `isAvailable(Activity)`: 检查是否可用

#### `BarcodeScanner.ScanCallback`
扫码结果回调接口：
- `onSuccess(String result)`: 扫码成功
- `onCancel()`: 扫码取消
- `onError(String error)`: 扫码失败

#### `FrameDecoder`（`scanner/decoder/`）
相机帧 / 位图解码器统一接口，由 `UnifiedScanActivity` 按引擎分派调用：
- `decode(ImageProxy)`: 解码一帧 YUV_420_888 相机图像
- `decodeBitmap(Bitmap)`: 解码一张位图（相册识别等场景）
- `close()`: 释放资源

### 实现类与引擎标签

引擎名称同时作为主界面左上角标题（隐藏切换入口）显示的短标签：

| 引擎类 | getName() 标签 | 说明 |
|---|---|---|
| `HuaweiBarcodeScanner` | `Huawei HMS` | 基于 HMS Core Scan Kit，`UnifiedScanActivity` 中走 `ScanUtil.decodeWithBitmap` |
| `MlKitBarcodeScanner` | `Google MLK` | **默认引擎**。ML Kit 17.3.0 独立版，模型打包在应用内，完全离线 |
| `ZxingCppBarcodeScanner` | `ZXING-CPP` | zxing-cpp 2.3.0（`io.github.zxing-cpp:android`），JNI 原生实现 |

#### `MlKitBarcodeScanner`（Google MLK，默认）
- 不需要 Google Play Services（模型打包在应用中，约 2-3MB）
- 使用 CameraX 处理相机预览
- 支持 13 种常用码制
- **未选择引擎或所选引擎不可用时，自动回落到此引擎**

#### `ZxingCppBarcodeScanner`（ZXING-CPP）
- 官方 Android 封装 `io.github.zxing-cpp:android:2.3.0`
- `BarcodeReader.read(ImageProxy)` 原生支持 YUV_420_888，JNI 直接读取 plane，**无 Java 层色彩空间转换开销**
- `BarcodeReader.read(Bitmap, crop, rotation)` 支持位图直接解码
- 开启 `tryHarder / tryRotate / tryInvert / tryDownscale`，全码制覆盖（QR/MicroQR/RMQR/Aztec/DataMatrix/PDF417/一维码全套）
- **条码识别调优**（针对曲面/反光一维码）：
  - `Binarizer.LOCAL_AVERAGE` 局部自适应二值化（瓶身受光不均必需）
  - `downscaleFactor=2 / downscaleThreshold=500` 降采样低通滤波，把反光断裂条纹"糊"连贯
  - `minLineCount=1`：曲面反光下整码可能只剩一条清晰扫描线；EAN-13/UPC-A/CODE-128 自带校验位，不引入误识
- 注意：该库仅提供**识别**，不含生成 API

#### `HuaweiBarcodeScanner`（Huawei HMS）
- 使用**增强版 scanplus**（`com.huawei.hms:scanplus`，非基础版 scan）——AI 增强算法，专门针对曲面、反光、污损、小码优化
- 实时流走 `HmsScanAnalyzer` + `MLFrame.fromByteBuffer`（YUV NV21 直读，`setPhotoMode(false)` 避免静态图慢速管线），quadrant 透传相机旋转角度
- 在华为/荣耀设备上优先可用（`isAvailable` 检查厂商 + HMS Core）

### 解码中枢

`UnifiedScanActivity` 是所有引擎共用的扫码页面：
- CameraX `ImageAnalysis` 产出 `ImageProxy` 帧（分辨率锁定 **1280x720**：4K 放大反光噪点且降帧率，低于 720p 条纹分辨率不足）
- 支持点击对焦/测光（tap-to-focus + AE）：金属罐反光处可点按压低局部曝光，3 秒后自动恢复连续对焦
- 按 `ScanCallbackHolder.decoder` 标识分派：
  - `ML_KIT_STANDALONE` → ML Kit `InputImage`
  - `HUAWEI_SCAN_KIT` → YUV→JPEG→Bitmap → `ScanUtil.decodeWithBitmap`
  - `ZXING_CPP` → `ZxingCppFrameDecoder`
- `volatile processing` 背压控制 + `active` 防重复回调

### 工厂类

#### `BarcodeScannerFactory`
- `ScannerType` 枚举：`HUAWEI_SCAN_KIT / ML_KIT_STANDALONE / ZXING_CPP`，每个枚举携带展示标签（`getLabel()`）
- `getDefaultScanner()`: 默认 ML Kit 独立版
- `getAvailableScanner(Activity)`: 自动降级 华为 → ML Kit → ZXing-CPP
- `createScanner(ScannerType)`: 创建指定类型扫码器

## 引擎切换交互

- **入口**：点击主界面左上角标题（Logo 文字，显示当前引擎标签）
- 弹出"选择解码方案"对话框，当前引擎加粗高亮，不可用项置灰
- 选择后持久化到 `SharedPreferences`（`proscan_prefs` / `scanner_type`）
- **标题文字实时显示当前引擎标签**（如 `Google MLK`）
- 初始化时未选择引擎或旧配置值失效（如已删除的 `GOOGLE_ML_KIT`），自动回落到 `Google MLK`

## 使用方法

### 基本使用

```java
// 使用当前引擎（默认 ML Kit 独立版本）
BarcodeScanner scanner = BarcodeScannerFactory.getDefaultScanner();

// 启动扫码
scanner.startScan(this, new BarcodeScanner.ScanCallback() {
    @Override
    public void onSuccess(String result) { /* 处理结果 */ }

    @Override
    public void onCancel() { /* 用户取消 */ }

    @Override
    public void onError(String error) { /* 扫码失败 */ }
});
```

### 指定扫码实现

```java
BarcodeScanner scanner = BarcodeScannerFactory.createScanner(
    BarcodeScannerFactory.ScannerType.HUAWEI_SCAN_KIT);     // Huawei HMS
BarcodeScanner scanner = BarcodeScannerFactory.createScanner(
    BarcodeScannerFactory.ScannerType.ML_KIT_STANDALONE);   // Google MLK（默认）
BarcodeScanner scanner = BarcodeScannerFactory.createScanner(
    BarcodeScannerFactory.ScannerType.ZXING_CPP);           // ZXING-CPP
```

## 配置

### build.gradle（app）

```gradle
// ML Kit Barcode Scanning 独立版本（不需要 Google Play Services）
implementation 'com.google.mlkit:barcode-scanning:17.3.0'

// ZXing-CPP Android 封装（含 JNI .so）
implementation 'io.github.zxing-cpp:android:2.3.0'

// ZXing core（仅用于生成二维码/条形码，识别引擎已切换）
implementation 'com.google.zxing:core:3.5.2'

// CameraX（注意：ML Kit 17.3.0 / zxing-cpp 2.3.0 会将 CameraX 原子组升级至 1.4.1，
// 因此 compileSdk/targetSdk 需 >= 34）
def camerax_version = "1.2.3"
implementation "androidx.camera:camera-core:${camerax_version}"
implementation "androidx.camera:camera-camera2:${camerax_version}"
implementation "androidx.camera:camera-lifecycle:${camerax_version}"
implementation "androidx.camera:camera-view:${camerax_version}"

// 华为 HMS Core Scan Kit
implementation 'com.huawei.hms:scan:2.12.0.300'
```

### 架构（ABI）

```gradle
defaultConfig {
    ndk {
        abiFilters 'armeabi-v7a', 'arm64-v8a'
    }
}
```
zxing-cpp 的 JNI 库（`libzxingcpp_android.so`）按此配置打入对应 ABI 目录，其余（x86/x86_64）被排除以控制体积。

### compileSdk / targetSdk

- **compileSdkVersion / targetSdkVersion: 34**（CameraX 1.4.1 由 ML Kit 17.3.0 与 zxing-cpp 2.3.0 传递引入，要求 compileSdk ≥ 34）
- **minSdkVersion: 30**（Android 11）

## 引擎能力对比

| 能力 | Huawei HMS | Google MLK | ZXING-CPP |
|---|---|---|---|
| 相机实时帧 | ✅ | ✅ | ✅（YUV 直读） | ✅（Y plane→GrayU8 复用） |
| 相册 Bitmap | ✅ | ✅ | ✅ | ✅ |
| 完全离线 | ❌（需 HMS Core） | ✅ | ✅ | ✅ |
| 全码制 | ✅ | 13 种 | ✅（全 + RMQR/DX-Film） | QR / MicroQR / Aztec |
| 反色识别 | ✅ | ✅ | ✅（tryInvert） | 部分 |
| 旋转识别 | ✅ | ✅ | ✅（tryRotate） | 部分 |
| 依赖体积 | 低（华为预装） | ~3MB 模型 | ~1.5MB JNI | 纯 Java ~2MB |

## ProGuard / R8

`app/proguard-rules.pro` 已包含：
- `zxingcpp.**` keep（JNI 绑定类不可混淆）
- ML Kit / ZXing keep 规则保留
- 华为 HMS keep 规则保留

## 扩展新的扫码实现

1. 实现 `BarcodeScanner` 接口（引擎壳，负责 startScan + 标识 + isAvailable）
2. 如需自定义解码逻辑，实现 `FrameDecoder` 接口（相机帧 + Bitmap 双路径）
3. 在 `BarcodeScannerFactory` 中添加枚举值与创建逻辑
4. 在 `UnifiedScanActivity` 中按 decoder 标识分派

## 选择建议

### 中国区 / 华为设备：Huawei HMS
- 华为官方维护，识别速度快、准确率高

### 国际区 / 通用设备：Google MLK（默认）
- 完全离线、不依赖任何设备厂商服务
- 模型打包在应用内，行为一致可预期

### 性能优先：ZXING-CPP
- C++ 原生解码，全码制覆盖，支持 tryRotate/tryInvert/tryDownscale
- YUV 直读，无 Java 层转换开销

## 参考文档

- [华为 HMS Core Scan Kit](https://developer.huawei.com/consumer/cn/doc/HMSCore-Guides/android-build-scan-capabilities-0000001050042010)
- [ML Kit Barcode Scanning](https://developers.google.com/ml-kit/vision/barcode-scanning/android)
- [ZXing-CPP Android](https://github.com/zxing-cpp/zxing-cpp)
- [CameraX](https://developer.android.com/training/camerax)
