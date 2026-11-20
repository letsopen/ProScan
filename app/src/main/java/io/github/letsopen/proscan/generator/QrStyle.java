package io.github.letsopen.proscan.generator;

/** QR 渲染预设；颜色保持克制的高对比度，不使用高饱和大面积色块。 */
public enum QrStyle {
    CLASSIC("经典极简", 0xFF111111, 0xFF111111),
    DOTS("圆润点阵", 0xFF1C1C1E, 0xFF1C1C1E),
    LIQUID("液态融合", 0xFF111111, 0xFF111111),
    GRADIENT("高级渐变", 0xFF0A2540, 0xFF007AFF);

    private final String label;
    private final int startColor;
    private final int endColor;
    QrStyle(String label, int startColor, int endColor) {
        this.label = label;
        this.startColor = startColor;
        this.endColor = endColor;
    }
    public String getLabel() { return label; }
    public int getStartColor() { return startColor; }
    public int getEndColor() { return endColor; }
}
