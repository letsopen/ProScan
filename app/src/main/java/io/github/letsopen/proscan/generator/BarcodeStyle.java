package io.github.letsopen.proscan.generator;

import android.graphics.Color;

/** 条码风格与颜色配置。条纹的水平宽度始终由原始矩阵决定。 */
public enum BarcodeStyle {
    CLASSIC("标准商务", 0xFF111111, 0xFF1D1D1F, 0f),
    SOFT("柔角现代", 0xFF1C1C1E, 0xFF86868B, 3f),
    MINIMAL("极简标签", 0xFF0A2540, 0xFF6E6E73, 0f);

    private final String label;
    private final int foreground;
    private final int textColor;
    private final float endRadius;

    BarcodeStyle(String label, int foreground, int textColor, float endRadius) {
        this.label = label;
        this.foreground = foreground;
        this.textColor = textColor;
        this.endRadius = endRadius;
    }
    public String getLabel() { return label; }
    public int getForeground() { return foreground; }
    public int getTextColor() { return textColor; }
    public float getEndRadius() { return endRadius; }
}
