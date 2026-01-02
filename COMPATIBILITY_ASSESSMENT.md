# Android 10 (API 29) 兼容性评估

## 配置总结

- **minSdkVersion**: 29 (Android 10)
- **targetSdkVersion**: 33 (Android 13)
- **compileSdkVersion**: 33

## 依赖兼容性评估

### ✅ 已确认兼容的依赖

1. **AndroidX 组件**
   - `androidx.appcompat:appcompat:1.6.1` - ✅ 支持 API 29
   - `com.google.android.material:material:1.9.0` - ✅ 支持 API 29
   - `androidx.constraintlayout:constraintlayout:2.1.4` - ✅ 支持 API 29

2. **CameraX 1.2.3**
   - ✅ 最低支持 API 21，完全兼容 API 29
   - 所有组件（core, camera2, lifecycle, view）都兼容

3. **ZXing**
   - `com.journeyapps:zxing-android-embedded:4.3.0` - ✅ 支持 API 29
   - `com.google.zxing:core:3.5.2` - ✅ 支持 API 29

4. **ML Kit**
   - `com.google.mlkit:barcode-scanning:17.2.0` - ✅ 最低支持 API 19，完全兼容 API 29
   - `com.google.android.gms:play-services-code-scanner:16.1.0` - ✅ 最低支持 API 23，完全兼容 API 29

5. **华为 HMS Core Scan Kit**
   - `com.huawei.hms:scan:2.12.0.300` - ✅ 最低支持 API 19，完全兼容 API 29

### ⚠️ 需要验证的依赖

1. **OpenCV**
   - `org.opencv:opencv:4.9.0` - ⚠️ 需要验证
   - **注意**: 如果这个包不包含 Android 特定的类（如 `BaseLoaderCallback`, `OpenCVLoader`），可能需要：
     - 使用 OpenCV Manager 动态加载
     - 或者使用静态库集成
     - 或者使用其他 OpenCV Android 包

## 功能兼容性

### ✅ 完全支持的功能

1. **ZXing 扫码** - ✅ 完全支持
2. **ML Kit 独立版本扫码** - ✅ 完全支持
3. **Google Code Scanner** - ✅ 完全支持（需要 Google Play Services）
4. **华为 Scan Kit** - ✅ 完全支持（需要 HMS Core）

### ⚠️ 需要验证的功能

1. **微信 OpenCV 二维码识别** - ⚠️ 取决于 OpenCV 依赖是否正确

## 已修复的问题

1. ✅ **minSdkVersion 提升到 29**
   - 解决了 adaptive-icon 需要 API 26 的问题
   - 符合最低支持 Android 10 的要求

2. ✅ **CameraX 版本降级到 1.2.3**
   - 避免 SDK 版本要求过高
   - 完全兼容 API 29

## 建议

1. **OpenCV 依赖验证**
   - 如果 `org.opencv:opencv:4.9.0` 不包含 Android 类，考虑：
     - 移除微信 OpenCV 二维码识别功能
     - 或者使用其他 OpenCV Android 集成方式
     - 或者使用 OpenCV Manager

2. **测试建议**
   - 在 Android 10 设备上测试所有扫码功能
   - 特别验证 ML Kit 和 ZXing 的兼容性
   - 如果使用华为设备，测试华为 Scan Kit

3. **降级方案**
   - 如果 OpenCV 无法正常工作，应用会自动降级到其他扫码方案
   - 优先级：微信 → 华为 → ML Kit → ZXing

## 最低系统要求总结

- **最低 Android 版本**: Android 10 (API 29) ✅
- **推荐 Android 版本**: Android 10+ (API 29+)
- **目标 Android 版本**: Android 13 (API 33)

