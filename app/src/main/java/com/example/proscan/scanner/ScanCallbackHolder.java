package com.example.proscan.scanner;

/** 当前扫码会话的回调与引擎信息。由扫码页销毁时清理。 */
public class ScanCallbackHolder {
    private static BarcodeScanner.ScanCallback callback;
    private static String decoder;
    public static void set(BarcodeScanner.ScanCallback cb, String d) { callback = cb; decoder = d; }
    public static BarcodeScanner.ScanCallback get() { return callback; }
    public static String getDecoder() { return decoder; }
    public static void setDecoder(String d) { decoder = d; }
    public static void clear() { callback = null; decoder = null; }
}
