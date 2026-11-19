package com.example.proscan.scanner;

import android.app.Activity;

import com.example.proscan.scanner.impl.HuaweiBarcodeScanner;
import com.example.proscan.scanner.impl.MlKitBarcodeScanner;
import com.example.proscan.scanner.impl.ZxingCppBarcodeScanner;

/**
 * 扫码器工厂类
 * 用于创建和管理不同的扫码实现
 * <p>
 * 引擎优先级：华为 HMS -> ML Kit -> ZXing-CPP
 */
public class BarcodeScannerFactory {

    /**
     * 扫码实现类型
     */
    public enum ScannerType {
        HUAWEI_SCAN_KIT("Huawei HMS"),     // 华为 HMS Core Scan Kit
        ML_KIT_STANDALONE("Google MLK"),   // ML Kit Barcode Scanning 独立版本（默认，不需要 Google Play Services）
        ZXING_CPP("ZXING-CPP");            // ZXing-CPP（原生实现）

        private final String label;

        ScannerType(String label) {
            this.label = label;
        }

        /** 展示用短标签 */
        public String getLabel() {
            return label;
        }
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
            case HUAWEI_SCAN_KIT:
                return new HuaweiBarcodeScanner();
            case ML_KIT_STANDALONE:
                return new MlKitBarcodeScanner();
            case ZXING_CPP:
                return new ZxingCppBarcodeScanner();
            default:
                return new MlKitBarcodeScanner();
        }
    }

    /**
     * 获取可用的扫码器（智能降级：华为 -> ML Kit -> ZXing-CPP）
     */
    public static BarcodeScanner getAvailableScanner(Activity activity) {
        // 1. 尝试使用华为 Scan Kit（仅在华为设备上）
        HuaweiBarcodeScanner huaweiScanner = new HuaweiBarcodeScanner();
        if (huaweiScanner.isAvailable(activity)) {
            return huaweiScanner;
        }

        // 2. ML Kit 独立版本（不依赖外部服务，自带模型）
        MlKitBarcodeScanner mlKitScanner = new MlKitBarcodeScanner();
        if (mlKitScanner.isAvailable(activity)) {
            return mlKitScanner;
        }

        // 3. 最终降级到 ZXing-CPP
        return new ZxingCppBarcodeScanner();
    }

    /**
     * 获取所有可用的扫码器名称
     */
    public static String[] getAvailableScannerNames(Activity activity) {
        java.util.List<String> names = new java.util.ArrayList<>();

        HuaweiBarcodeScanner huaweiScanner = new HuaweiBarcodeScanner();
        if (huaweiScanner.isAvailable(activity)) {
            names.add(huaweiScanner.getName());
        }

        MlKitBarcodeScanner mlKitScanner = new MlKitBarcodeScanner();
        if (mlKitScanner.isAvailable(activity)) {
            names.add(mlKitScanner.getName());
        }

        ZxingCppBarcodeScanner zxingCppScanner = new ZxingCppBarcodeScanner();
        if (zxingCppScanner.isAvailable(activity)) {
            names.add(zxingCppScanner.getName());
        }

        return names.toArray(new String[0]);
    }
}
