package com.example.proscan.scanner;

public class ScanCallbackHolder {
    private static BarcodeScanner.ScanCallback callback;
    private static String decoder;
    public static void set(BarcodeScanner.ScanCallback cb, String d) { callback = cb; decoder = d; }
    public static BarcodeScanner.ScanCallback get() { return callback; }
    public static String getDecoder() { return decoder; }
    public static void clear() { callback = null; decoder = null; }
}
