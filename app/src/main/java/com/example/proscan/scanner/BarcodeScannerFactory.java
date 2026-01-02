package com.example.proscan.scanner;

import android.app.Activity;

import com.example.proscan.scanner.impl.GoogleCodeScanner;
import com.example.proscan.scanner.impl.HuaweiBarcodeScanner;
import com.example.proscan.scanner.impl.MlKitBarcodeScanner;
import com.example.proscan.scanner.impl.WeChatQRCodeScanner;
import com.example.proscan.scanner.impl.ZxingBarcodeScanner;

/**
 * 扫码器工厂类
 * 用于创建和管理不同的扫码实现
 */
public class BarcodeScannerFactory {
    
    /**
     * 扫码实现类型
     */
    public enum ScannerType {
        WECHAT_QRCODE,      // 微信 OpenCV 二维码识别
        HUAWEI_SCAN_KIT,    // 华为 HMS Core Scan Kit
        GOOGLE_ML_KIT,      // Google ML Kit Code Scanner（需要 Google Play Services）
        ML_KIT_STANDALONE,  // ML Kit Barcode Scanning 独立版本（不需要 Google Play Services）
        ZXING               // ZXing
    }
    
    private static ScannerType defaultType = ScannerType.ML_KIT_STANDALONE;
    
    /**
     * 设置默认扫码实现类型
     */
    public static void setDefaultType(ScannerType type) {
        defaultType = type;
    }
    
    /**
     * 获取默认扫码器
     */
    public static BarcodeScanner getDefaultScanner() {
        return createScanner(defaultType);
    }
    
    /**
     * 创建指定类型的扫码器
     */
    public static BarcodeScanner createScanner(ScannerType type) {
        switch (type) {
            case WECHAT_QRCODE:
                return new WeChatQRCodeScanner();
            case HUAWEI_SCAN_KIT:
                return new HuaweiBarcodeScanner();
            case GOOGLE_ML_KIT:
                return new GoogleCodeScanner();
            case ML_KIT_STANDALONE:
                return new MlKitBarcodeScanner();
            case ZXING:
                return new ZxingBarcodeScanner();
            default:
                return new MlKitBarcodeScanner();
        }
    }
    
    /**
     * 获取可用的扫码器（智能选择：优先微信，然后华为，然后ML Kit独立版本，最后ZXing）
     * 注意：Google Code Scanner 需要 Google Play Services，所以不在自动选择列表中
     */
    public static BarcodeScanner getAvailableScanner(Activity activity) {
        // 优先使用微信 OpenCV 二维码识别（如果模型文件存在）
        WeChatQRCodeScanner weChatScanner = new WeChatQRCodeScanner();
        if (weChatScanner.isAvailable(activity)) {
            return weChatScanner;
        }
        
        // 其次使用华为 Scan Kit（如果可用）
        HuaweiBarcodeScanner huaweiScanner = new HuaweiBarcodeScanner();
        if (huaweiScanner.isAvailable(activity)) {
            return huaweiScanner;
        }
        
        // 然后使用 ML Kit 独立版本（不需要 Google Play Services）
        MlKitBarcodeScanner mlKitScanner = new MlKitBarcodeScanner();
        if (mlKitScanner.isAvailable(activity)) {
            return mlKitScanner;
        }
        
        // 降级到 ZXing
        return new ZxingBarcodeScanner();
    }
    
    /**
     * 获取所有可用的扫码器名称
     */
    public static String[] getAvailableScannerNames(Activity activity) {
        java.util.List<String> names = new java.util.ArrayList<>();
        
        // 微信 OpenCV 二维码识别
        WeChatQRCodeScanner weChatScanner = new WeChatQRCodeScanner();
        if (weChatScanner.isAvailable(activity)) {
            names.add(weChatScanner.getName());
        }
        
        // 华为 Scan Kit
        HuaweiBarcodeScanner huaweiScanner = new HuaweiBarcodeScanner();
        if (huaweiScanner.isAvailable(activity)) {
            names.add(huaweiScanner.getName());
        }
        
        // ML Kit 独立版本（不需要 Google Play Services）
        MlKitBarcodeScanner mlKitScanner = new MlKitBarcodeScanner();
        if (mlKitScanner.isAvailable(activity)) {
            names.add(mlKitScanner.getName());
        }
        
        // Google Code Scanner（需要 Google Play Services）
        GoogleCodeScanner googleScanner = new GoogleCodeScanner();
        if (googleScanner.isAvailable(activity)) {
            names.add(googleScanner.getName());
        }
        
        // ZXing
        ZxingBarcodeScanner zxingScanner = new ZxingBarcodeScanner();
        if (zxingScanner.isAvailable(activity)) {
            names.add(zxingScanner.getName());
        }
        
        return names.toArray(new String[0]);
    }
}

