package com.example.proscan.scanner;

import androidx.annotation.NonNull;

/** 一次成功识别的不可变结果，包含原始文本与引擎实际返回的码制。 */
public final class ScanResult {
    private final String text;
    private final String format;
    private final String engine;

    public ScanResult(@NonNull String text, @NonNull String format, @NonNull String engine) {
        this.text = text;
        this.format = format;
        this.engine = engine;
    }

    @NonNull public String getText() { return text; }
    @NonNull public String getFormat() { return format; }
    @NonNull public String getEngine() { return engine; }
}
