# 扫码架构说明

## 概述

本项目已重构为可插拔的扫码架构，支持多种扫码实现。当前支持：
- **华为 HMS Core Scan Kit**（中国区推荐方案）
- **ML Kit Barcode Scanning 独立版本**（不需要 Google Play Services）
- **Google ML Kit Code Scanner**（需要 Google Play Services）
- **ZXing**（备用方案）

## 架构设计

### 核心接口

#### `BarcodeScanner`
扫码接口，定义了统一的扫码方法：
- `startScan(Activity, ScanCallback)`: 启动扫码
- `getName()`: 获取实现名称
- `isAvailable(Activity)`: 检查是否可用

#### `BarcodeScanner.ScanCallback`
扫码结果回调接口：
- `onSuccess(String result)`: 扫码成功
- `onCancel()`: 扫码取消
- `onError(String error)`: 扫码失败

### 实现类

#### `HuaweiBarcodeScanner`
- 基于华为 HMS Core Scan Kit
- **优势**：
  - **专为中国市场优化**，在华为设备上性能优秀
  - 支持多种条码格式
  - 识别速度快、准确率高
  - 华为官方维护，稳定可靠
- **要求**：需要 HMS Core（华为设备通常已预装，其他设备需要安装）
- **注意**：在华为设备上优先使用此方案

#### `MlKitBarcodeScanner`
- 基于 ML Kit Barcode Scanning API 独立版本
- **优势**：
  - **不需要 Google Play Services**（模型打包在应用中）
  - 支持多种条码格式
  - 使用 CameraX 处理相机预览
  - 完全离线工作
- **要求**：Android API 23+，需要相机权限
- **注意**：模型会打包到应用中，增加约 2-3MB 体积

#### `GoogleCodeScanner`
- 基于 Google ML Kit Code Scanner API
- **优势**：
  - 不需要相机权限（由 Google Play Services 处理）
  - 支持自动缩放（16.1.0+）
  - 支持多种条码格式
  - 更小的应用体积
- **要求**：Android API 23+，**需要 Google Play Services**

#### `ZxingBarcodeScanner`
- 基于 ZXing 库
- **优势**：
  - 完全离线工作
  - 不依赖 Google Play Services
  - 可自定义 UI
- **要求**：需要相机权限

### 工厂类

#### `BarcodeScannerFactory`
管理扫码实现的创建和选择：
- `getDefaultScanner()`: 获取默认扫码器
- `getAvailableScanner(Activity)`: 自动选择可用的扫码器（优先 Google ML Kit）
- `createScanner(ScannerType)`: 创建指定类型的扫码器

## 使用方法

### 基本使用

```java
// 自动选择可用的扫码器（智能选择：优先华为，然后ML Kit独立版本，最后ZXing）
BarcodeScanner scanner = BarcodeScannerFactory.getAvailableScanner(this);

// 启动扫码
scanner.startScan(this, new BarcodeScanner.ScanCallback() {
    @Override
    public void onSuccess(String result) {
        // 处理扫码结果
    }
    
    @Override
    public void onCancel() {
        // 处理取消
    }
    
    @Override
    public void onError(String error) {
        // 处理错误
    }
});
```

### 指定扫码实现

```java
// 使用华为 Scan Kit（推荐，中国区设备）
BarcodeScanner scanner = BarcodeScannerFactory.createScanner(
    BarcodeScannerFactory.ScannerType.HUAWEI_SCAN_KIT
);

// 使用 ML Kit 独立版本（推荐，不需要 Google Play Services）
BarcodeScanner scanner = BarcodeScannerFactory.createScanner(
    BarcodeScannerFactory.ScannerType.ML_KIT_STANDALONE
);

// 使用 Google ML Kit Code Scanner（需要 Google Play Services）
BarcodeScanner scanner = BarcodeScannerFactory.createScanner(
    BarcodeScannerFactory.ScannerType.GOOGLE_ML_KIT
);

// 使用 ZXing
BarcodeScanner scanner = BarcodeScannerFactory.createScanner(
    BarcodeScannerFactory.ScannerType.ZXING
);
```

### 处理 Activity Result

华为 Scan Kit 和 ZXing 都使用 Activity Result 机制，需要在 `onActivityResult` 中处理：

```java
@Override
protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    // 处理华为扫码结果
    if (HuaweiBarcodeScanner.handleActivityResult(requestCode, resultCode, data)) {
        return; // 华为已处理
    }
    
    // 处理ZXing扫码结果
    if (ZxingBarcodeScanner.handleActivityResult(requestCode, resultCode, data)) {
        return; // ZXing已处理
    }
    
    super.onActivityResult(requestCode, resultCode, data);
}
```

## 配置

### build.gradle

```gradle
// 在 settings.gradle 中添加华为 Maven 仓库
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url 'https://developer.huawei.com/repo/' }
    }
}

dependencies {
    // ZXing（备用）
    implementation 'com.journeyapps:zxing-android-embedded:4.3.0'
    implementation 'com.google.zxing:core:3.5.2'
    
    // Google ML Kit Code Scanner（需要 Google Play Services）
    implementation 'com.google.android.gms:play-services-code-scanner:16.1.0'
    
    // ML Kit Barcode Scanning 独立版本（不需要 Google Play Services）
    implementation 'com.google.mlkit:barcode-scanning:17.2.0'
    
    // CameraX（用于 ML Kit 独立版本的相机预览）
    def camerax_version = "1.3.0"
    implementation "androidx.camera:camera-core:${camerax_version}"
    implementation "androidx.camera:camera-camera2:${camerax_version}"
    implementation "androidx.camera:camera-lifecycle:${camerax_version}"
    implementation "androidx.camera:camera-view:${camerax_version}"
    
    // 华为 HMS Core Scan Kit
    implementation 'com.huawei.hms:scan:2.12.0.300'
}
```

### AndroidManifest.xml

```xml
<!-- Google ML Kit Code Scanner 自动下载模块 -->
<meta-data
    android:name="com.google.mlkit.vision.DEPENDENCIES"
    android:value="barcode_ui" />

<!-- 相机权限（ZXing 需要） -->
<uses-permission android:name="android.permission.CAMERA" />
```

## 扩展新的扫码实现

要添加新的扫码实现，只需：

1. 实现 `BarcodeScanner` 接口
2. 在 `BarcodeScannerFactory` 中添加新的类型和创建逻辑

示例：

```java
public class NewScanner implements BarcodeScanner {
    @Override
    public void startScan(Activity activity, ScanCallback callback) {
        // 实现扫码逻辑
    }
    
    @Override
    public String getName() {
        return "New Scanner";
    }
    
    @Override
    public boolean isAvailable(Activity activity) {
        // 检查是否可用
        return true;
    }
}
```

## 选择建议

### 推荐方案（按地区）

#### 中国区：华为 Scan Kit
- ✅ **专为中国市场优化**，在华为设备上性能优秀
- ✅ 识别速度快、准确率高
- ✅ 华为官方维护，稳定可靠
- ⚠️ 需要 HMS Core（华为设备通常已预装）
- ⚠️ 非华为设备需要安装 HMS Core

#### 国际区：ML Kit 独立版本
- ✅ 不需要 Google Play Services
- ✅ 支持所有 Android 设备（包括没有 Google 服务的设备）
- ✅ 完全离线工作
- ⚠️ 应用体积增加约 2-3MB

### 备选方案

#### Google Code Scanner
- ✅ 不需要相机权限
- ✅ 应用体积更小
- ❌ 需要 Google Play Services
- ❌ 不支持没有 Google 服务的设备

#### ZXing
- ✅ 完全离线工作
- ✅ 不依赖任何 Google 服务或华为服务
- ✅ 可自定义 UI
- ⚠️ 需要相机权限
- ⚠️ 性能可能不如 ML Kit 或华为 Scan Kit

## 参考文档

- [华为 HMS Core Scan Kit](https://developer.huawei.com/consumer/cn/doc/HMSCore-Guides/android-build-scan-capabilities-0000001050042010)
- [ML Kit Barcode Scanning](https://developers.google.com/ml-kit/vision/barcode-scanning/android)
- [Google ML Kit Code Scanner](https://developers.google.com/ml-kit/vision/barcode-scanning/code-scanner)
- [ZXing Android Embedded](https://github.com/journeyapps/zxing-android-embedded)
- [CameraX](https://developer.android.com/training/camerax)

