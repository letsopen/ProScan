package com.example.proscan.scanner;

import android.app.Activity;

/**
 * 扫码接口，支持多种扫码实现
 */
public interface BarcodeScanner {
    
    /**
     * 扫码结果回调接口
     */
    interface ScanCallback {
        /**
         * 扫码成功
         * @param result 扫码结果内容
         */
        void onSuccess(String result);
        
        /**
         * 扫码取消
         */
        void onCancel();
        
        /**
         * 扫码失败
         * @param error 错误信息
         */
        void onError(String error);
    }
    
    /**
     * 启动扫码
     * @param activity 当前Activity
     * @param callback 扫码回调
     */
    void startScan(Activity activity, ScanCallback callback);
    
    /**
     * 获取扫码实现名称
     * @return 实现名称
     */
    String getName();
    
    /**
     * 检查是否可用
     * @param activity 当前Activity
     * @return 是否可用
     */
    boolean isAvailable(Activity activity);
}

