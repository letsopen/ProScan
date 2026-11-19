# Android 兼容性评估

## 配置总结

- **minSdkVersion**: 30 (Android 11)
- **targetSdkVersion**: 34 (Android 14)
- **compileSdkVersion**: 34

> v2.9 说明：compileSdk/targetSdk 从 33 升级到 34，原因是 ML Kit 17.3.0 与
> zxing-cpp 2.3.0 的传递依赖将 CameraX 原子组升级至 1.4.1（要求 compileSdk ≥ 34）。

## 依赖兼容性评估

### ✅ 已确认兼容的依赖

1. **AndroidX 组件**
   - `androidx.appcompat:appcompat:1.6.1` - ✅ 支持 API 30
   - `com.google.android.material:material:1.9.0` - ✅ 支持 API 30
   - `androidx.constraintlayout:constraintlayout:2.1.4` - ✅ 支持 API 30

2. **CameraX 1.4.1**（由 ML Kit 17.3.0 / zxing-cpp 2.3.0 传递引入）
   - ✅ 最低支持 API 21，完全兼容 API 30
   - 所有组件（core, camera2, lifecycle, video, view）都兼容

3. **ZXing-CPP**（`io.github.zxing-cpp:android:2.3.0`）
   - ✅ 纯本地解码，含 JNI 原生库（arm64-v8a / armeabi-v7a）
   - ✅ minSdk 24+，完全兼容 API 30
   - ✅ 原生支持 YUV_420_888 与 Bitmap 直接解码

5. **ML Kit**（`com.google.mlkit:barcode-scanning:17.3.0`）
   - ✅ 最低支持 API 21，完全兼容 API 30
   - ✅ 模型打包在应用中，完全离线

6. **华为 HMS Core Scan Kit**（`com.huawei.hms:scan:2.12.0.300`）
   - ✅ 最低支持 API 19，完全兼容 API 30

7. **ZXing core**（`com.google.zxing:core:3.5.2`，仅用于生成功能）
   - ✅ 纯 Java，无平台限制

## 功能兼容性

### ✅ 完全支持的功能

1. **华为 HMS 扫码** - ✅ 支持（华为/荣耀设备，需 HMS Core）
2. **ML Kit 独立版本扫码** - ✅ 支持（默认引擎，完全离线）
3. **ZXing-CPP 扫码** - ✅ 支持（全码制，YUV 直读 + Bitmap 解码）
4. **二维码/条形码生成** - ✅ 支持（zxing-core `MultiFormatWriter`）

### 引擎降级链

初始化未选择引擎或所选引擎不可用时，自动回落到默认引擎 **ML Kit 独立版本（Google MLK）**。
运行时自动降级顺序：华为 HMS → Google MLK → ZXING-CPP。

## 已知限制

1. **ZXing-CPP 仅支持识别**
   - `io.github.zxing-cpp:android` 未暴露生成 API（C++ 侧 `WriteBarcode` 尚未封装到 Android）
   - 因此生成功能保留 `com.google.zxing:core:3.5.2`

3. **CameraX 原子组版本**
   - ML Kit 17.3.0 与 zxing-cpp 2.3.0 均传递依赖 CameraX 1.4.1
   - CameraX 为原子组（atomic group），无法单独锁定旧版本
   - 因此 compileSdk 必须保持 ≥ 34

## 最低系统要求总结

- **最低 Android 版本**: Android 11 (API 30) ✅
- **目标 Android 版本**: Android 14 (API 34)
- **编译 SDK**: 34
