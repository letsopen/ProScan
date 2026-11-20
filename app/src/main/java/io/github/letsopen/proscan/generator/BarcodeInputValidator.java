package io.github.letsopen.proscan.generator;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.oned.EAN13Writer;

/** 一维条码的统一输入规则和中文错误信息。 */
public final class BarcodeInputValidator {
    public static final int CODE128_MAX_LENGTH = 32;
    private BarcodeInputValidator() {}

    /** 返回 null 表示合法；否则返回可直接展示给用户的中文说明。 */
    public static String validate(String content, String format) {
        if (content == null || content.isEmpty()) return "请输入条码内容";
        if ("EAN13".equals(format)) return validateEan13(content);
        return validateCode128(content);
    }

    private static String validateCode128(String content) {
        if (content.length() > CODE128_MAX_LENGTH) {
            return "内容过长，无法清晰生成条码（最多 32 个字符）。请改用二维码或缩短内容";
        }
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            // 仅接受可打印 ASCII 与空格，拒绝中文、全角字符、换行及其他控制字符。
            if (c < 32 || c > 126) {
                return "CODE 128 仅支持数字、英文字母、空格和常见标点，不能包含中文或换行";
            }
        }
        try {
            // 提前调用编码器发现非法组合，错误始终转成中文。
            new com.google.zxing.oned.Code128Writer().encode(content);
        } catch (Exception e) {
            return "当前内容无法生成 CODE 128，请检查英文字符或标点";
        }
        return null;
    }

    private static String validateEan13(String content) {
        if (!content.matches("^\\d+$")) return "EAN-13 仅支持数字，不能包含字母、中文或符号";
        if (content.length() != 12 && content.length() != 13) {
            return "EAN-13 需输入 12 位数字（自动补校验位），或含正确校验位的 13 位数字";
        }
        if (content.length() == 13) {
            int sum = 0;
            for (int i = 0; i < 12; i++) {
                int digit = content.charAt(i) - '0';
                sum += i % 2 == 0 ? digit : digit * 3;
            }
            int checksum = (10 - sum % 10) % 10;
            if (content.charAt(12) - '0' != checksum) {
                return "EAN-13 最后一位校验位不正确，请检查数字或输入前 12 位";
            }
        }
        try {
            new EAN13Writer().encode(content);
        } catch (IllegalArgumentException e) {
            return "当前数字不符合 EAN-13 规则，请检查长度与校验位";
        }
        return null;
    }
}
