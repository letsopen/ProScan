package io.github.letsopen.proscan.generator;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.letsopen.proscan.R;

/** 码图生成页：矩阵编码、Canvas 风格渲染和高清导出均与 UI 状态解耦。 */
public class CodeGeneratorActivity extends AppCompatActivity {
    public static final String EXTRA_CONTENT = "content";
    public static final String EXTRA_TYPE = "type";
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private ImageView preview;
    private View progress;
    private LinearLayout styleOptions;
    private CodeType type;
    private QrStyle qrStyle = QrStyle.CLASSIC;
    private BarcodeStyle barcodeStyle = BarcodeStyle.CLASSIC;
    private String barcodeFormat = "CODE128";
    private String content;
    private int generation;
    private Bitmap currentBitmap;
    private boolean destroyed;
    private boolean exporting;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_code_generator);
        type = "BARCODE".equals(getIntent().getStringExtra(EXTRA_TYPE)) ? CodeType.BARCODE : CodeType.QR;
        content = getIntent().getStringExtra(EXTRA_CONTENT);
        if (content == null) content = "";
        preview = findViewById(R.id.code_preview);
        progress = findViewById(R.id.generator_progress);
        styleOptions = findViewById(R.id.style_options);
        ((TextView) findViewById(R.id.generator_title)).setText(type == CodeType.QR ? "生成二维码" : "生成条形码");
        findViewById(R.id.barcode_formats).setVisibility(type == CodeType.QR ? View.GONE : View.VISIBLE);
        findViewById(R.id.button_generator_back).setOnClickListener(v -> finish());
        findViewById(R.id.button_save_image).setOnClickListener(v -> export(true));
        findViewById(R.id.button_share_image).setOnClickListener(v -> export(false));
        if (type == CodeType.QR) createQrStyles();
        else createBarcodeStyles();
        updateFormatButtons();
        regenerate();
        View header = findViewById(R.id.generator_header);
        View scroll = header.getParent() instanceof View ? (View) header.getParent() : header;
        int initialTop = header.getPaddingTop();
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.generator_root), (v, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            header.setPadding(header.getPaddingLeft(), initialTop + bars.top, header.getPaddingRight(), header.getPaddingBottom());
            scroll.setPadding(scroll.getPaddingLeft(), scroll.getPaddingTop(), scroll.getPaddingRight(), 32 + bars.bottom);
            return insets;
        });
    }

    private void createQrStyles() {
        for (QrStyle style : QrStyle.values()) addStyle(style.getLabel(), () -> qrStyle = style,
                () -> qrStyle == style);
    }

    private void createBarcodeStyles() {
        for (BarcodeStyle style : BarcodeStyle.values()) addStyle(style.getLabel(), () -> barcodeStyle = style,
                () -> barcodeStyle == style);
        findViewById(R.id.format_code128).setOnClickListener(v -> { barcodeFormat = "CODE128"; updateFormatButtons(); regenerate(); });
        findViewById(R.id.format_ean13).setOnClickListener(v -> { barcodeFormat = "EAN13"; updateFormatButtons(); regenerate(); });
    }

    private void addStyle(String label, Runnable setter, java.util.function.Supplier<Boolean> selected) {
        View item = getLayoutInflater().inflate(R.layout.item_code_style, styleOptions, false);
        TextView text = item.findViewById(R.id.style_label);
        text.setText(label);
        item.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            setter.run();
            regenerate();
        });
        styleOptions.addView(item);
        item.setTag(selected);
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) item.getLayoutParams();
        params.setMarginEnd(Math.round(10 * getResources().getDisplayMetrics().density));
        item.setLayoutParams(params);
    }

    private void refreshStyleSelection() {
        for (int i = 0; i < styleOptions.getChildCount(); i++) {
            View item = styleOptions.getChildAt(i);
            @SuppressWarnings("unchecked") java.util.function.Supplier<Boolean> selected =
                    (java.util.function.Supplier<Boolean>) item.getTag();
            boolean active = selected.get();
            item.setSelected(active);
            ((TextView) item.findViewById(R.id.style_label)).setTextColor(
                    getColor(active ? R.color.white : R.color.text_secondary));
        }
    }

    private void updateFormatButtons() {
        Button code128 = findViewById(R.id.format_code128);
        Button ean13 = findViewById(R.id.format_ean13);
        code128.setTextColor(getColor("CODE128".equals(barcodeFormat) ? R.color.accent_blue : R.color.text_secondary));
        ean13.setTextColor(getColor("EAN13".equals(barcodeFormat) ? R.color.accent_blue : R.color.text_secondary));
    }

    private void regenerate() {
        if (type == CodeType.BARCODE && "EAN13".equals(barcodeFormat)
                && !(content.matches("^\\d{12}$") || content.matches("^\\d{13}$"))) {
            Toast.makeText(this, "EAN-13 需输入 12 或 13 位数字", Toast.LENGTH_LONG).show();
        }
        int request = ++generation;
        refreshStyleSelection();
        progress.setVisibility(View.VISIBLE);
        setExportEnabled(false);
        CodeStyleConfig config = type == CodeType.QR ? CodeStyleConfig.qr(qrStyle)
                : CodeStyleConfig.barcode(barcodeStyle, barcodeFormat);
        executor.execute(() -> {
            Bitmap result = null;
            Exception error = null;
            try {
                if (type == CodeType.QR) {
                    result = StyledQrRenderer.render(CodeMatrixEncoder.encodeQr(content), config, CodeStyleConfig.QR_EXPORT_SIZE);
                } else {
                    result = StyledBarcodeRenderer.render(CodeMatrixEncoder.encodeBarcode(content, barcodeFormat),
                            config, CodeStyleConfig.BARCODE_EXPORT_WIDTH, CodeStyleConfig.BARCODE_EXPORT_HEIGHT, content);
                }
            } catch (Exception e) { error = e; }
            Bitmap finalResult = result;
            Exception finalError = error;
            runOnUiThread(() -> {
                if (destroyed || request != generation) {
                    if (finalResult != null) finalResult.recycle();
                    return;
                }
                progress.setVisibility(View.GONE);
                if (finalError != null) {
                    Toast.makeText(this, finalError.getMessage(), Toast.LENGTH_LONG).show();
                    return;
                }
                if (currentBitmap != null) currentBitmap.recycle();
                currentBitmap = finalResult;
                preview.setImageBitmap(currentBitmap);
                preview.animate().alpha(0.65f).setDuration(0).withEndAction(() ->
                        preview.animate().alpha(1f).setDuration(150).start()).start();
                setExportEnabled(true);
            });
        });
    }

    private void setExportEnabled(boolean enabled) {
        findViewById(R.id.button_save_image).setEnabled(enabled && !exporting);
        findViewById(R.id.button_share_image).setEnabled(enabled && !exporting);
    }

    private void export(boolean save) {
        if (currentBitmap == null || exporting) return;
        exporting = true;
        setExportEnabled(false);
        String name = "ProScan_" + System.currentTimeMillis();
        // 导出固定尺寸原图，不缩放屏幕预览，不引入透明通道。
        Bitmap bitmap = currentBitmap.copy(Bitmap.Config.ARGB_8888, false);
        executor.execute(() -> {
            Exception error = null;
            try {
                if (save) CodeImageExporter.saveToGallery(this, bitmap, name);
                else CodeImageExporter.share(this, bitmap, name);
            } catch (Exception e) { error = e; }
            finally { bitmap.recycle(); }
            Exception finalError = error;
            runOnUiThread(() -> {
                if (destroyed) return;
                exporting = false;
                setExportEnabled(true);
                if (finalError != null) Toast.makeText(this, "导出失败：" + finalError.getMessage(), Toast.LENGTH_LONG).show();
                else if (save) Toast.makeText(this, "已保存到相册 · Pictures/ProScan", Toast.LENGTH_SHORT).show();
            });
        });
    }

    @Override protected void onDestroy() {
        destroyed = true;
        generation++;
        executor.shutdown();
        if (currentBitmap != null) { currentBitmap.recycle(); currentBitmap = null; }
        super.onDestroy();
    }
}
