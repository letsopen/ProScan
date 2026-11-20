package com.example.proscan;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import android.Manifest;
import android.content.pm.PackageManager;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import androidx.appcompat.app.AppCompatActivity;

import com.example.proscan.db.HistoryDbHelper;
import com.example.proscan.scanner.BarcodeScanner;
import com.example.proscan.scanner.BarcodeScannerFactory;

// 生成功能仅用 zxing-core；识别由 ML Kit、HMS 和 ZXing-CPP 完成
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;

import android.app.Dialog;
import android.widget.ImageView;
import android.view.Window;
import android.view.ViewGroup;
import android.graphics.Matrix;

import android.content.SharedPreferences;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;

import android.graphics.Bitmap;
import android.graphics.Color;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private EditText editTextUrl;
    private Button buttonScan;
    private Button buttonPaste;
    private Button btnVisit;
    private ImageButton buttonHistory;
    private android.view.View textTitle;
    private TextView textEngineLabel;
    private HistoryDbHelper dbHelper;
    private BarcodeScanner barcodeScanner;
    private Dialog generatingDialog;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final String PREFS = "proscan_prefs";
    private static final String KEY_SCANNER_TYPE = "scanner_type";

    private static final int PERMISSION_REQUEST_CAMERA = 100;
    private androidx.activity.result.ActivityResultLauncher<Intent> historyLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_main);

        dbHelper = HistoryDbHelper.getInstance(this);
        historyLauncher = registerForActivityResult(
                new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(), result -> {
                    Intent data = result.getData();
                    if (result.getResultCode() == RESULT_OK && data != null) {
                        String content = data.getStringExtra("content");
                        if (content != null) editTextUrl.setText(content);
                    }
                });
        editTextUrl = findViewById(R.id.editTextUrl);
        buttonScan = findViewById(R.id.buttonScan);
        buttonPaste = findViewById(R.id.buttonPaste);
        btnVisit = findViewById(R.id.btnVisit);
        buttonHistory = findViewById(R.id.buttonHistory);
        textTitle = findViewById(R.id.textTitle);
        textEngineLabel = findViewById(R.id.text_engine_label);

        // 新用户、无有效偏好或上次选择已不可用时，默认 GOOGLE ML。
        syncSelectedScanner();

        textTitle.setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
            showScannerChoiceDialog();
        });
        applyMainInsets();

        buttonScan.setOnClickListener(v -> {
            if (checkCameraPermission()) {
                startScan();
            } else {
                requestCameraPermission();
            }
        });
        
        buttonPaste.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard.hasPrimaryClip()) {
                ClipData.Item item = clipboard.getPrimaryClip().getItemAt(0);
                String text = item.getText().toString();
                if (text == null || text.isEmpty()) {
                    Toast.makeText(this, "剪贴板为空", Toast.LENGTH_SHORT).show();
                    return;
                }
                editTextUrl.setText(text);
                Toast.makeText(this, "已粘贴", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "剪贴板为空", Toast.LENGTH_SHORT).show();
            }
        });

        btnVisit.setOnClickListener(v -> {
            String url = editTextUrl.getText().toString().trim();
            if (!url.isEmpty()) {
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(this, "无法打开链接", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "请输入链接", Toast.LENGTH_SHORT).show();
            }
        });

        buttonHistory.setOnClickListener(v -> {
            historyLauncher.launch(new Intent(this, HistoryActivity.class));
        });

        Button btnBarcode = findViewById(R.id.btnBarcode);
        Button btnQRCode = findViewById(R.id.btnQRCode);

        btnBarcode.setOnClickListener(v -> generateBarcode());
        btnQRCode.setOnClickListener(v -> generateQRCode());
    }

    private void applyMainInsets() {
        final int topPadding = getResources().getDimensionPixelSize(R.dimen.space_16);
        final int bottomPadding = getResources().getDimensionPixelSize(R.dimen.space_32);
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_content), (v, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(
                    androidx.core.view.WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), bars.top + topPadding, v.getPaddingRight(), bars.bottom + bottomPadding);
            return androidx.core.view.WindowInsetsCompat.CONSUMED;
        });
    }

    private void showScannerChoiceDialog() {
        java.util.List<BarcodeScannerFactory.ScannerType> typeList = new java.util.ArrayList<>();
        // 仅展示当前设备支持的引擎；非华为/荣耀设备不会出现 HMS。
        typeList.addAll(BarcodeScannerFactory.getSupportedTypes(this));
        java.util.List<CharSequence> names = new java.util.ArrayList<>();
        java.util.List<Boolean> availables = new java.util.ArrayList<>();
        String currentName = barcodeScanner != null ? barcodeScanner.getName() : null;
        int highlightColor = 0xFF1D1D1F;
        for (int i = 0; i < typeList.size(); i++) {
            BarcodeScanner s = BarcodeScannerFactory.createScanner(typeList.get(i));
            boolean available = s.isAvailable(this);
            String base = s.getName() + (available ? "" : "（不可用）");
            android.text.SpannableString ss = new android.text.SpannableString(base);
            if (currentName != null && currentName.equals(s.getName())) {
                ss.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 0, base.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                ss.setSpan(new android.text.style.ForegroundColorSpan(highlightColor), 0, base.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            if (!available) {
                ss.setSpan(new android.text.style.ForegroundColorSpan(0xFF9E9E9E), 0, base.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            names.add(ss);
            availables.add(available);
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("选择解码方案");
        android.widget.ListAdapter adapter = new android.widget.BaseAdapter() {
            @Override public int getCount() { return names.size(); }
            @Override public Object getItem(int position) { return names.get(position); }
            @Override public long getItemId(int position) { return position; }
            @Override public android.view.View getView(int position, android.view.View convertView, android.view.ViewGroup parent) {
                android.widget.TextView tv = convertView instanceof android.widget.TextView ? (android.widget.TextView) convertView : new android.widget.TextView(MainActivity.this);
                tv.setPadding((int)(getResources().getDisplayMetrics().density*20), (int)(getResources().getDisplayMetrics().density*12), (int)(getResources().getDisplayMetrics().density*20), (int)(getResources().getDisplayMetrics().density*12));
                tv.setText(names.get(position));
                tv.setEnabled(availables.get(position));
                if (!availables.get(position)) tv.setAlpha(0.5f); else tv.setAlpha(1f);
                return tv;
            }
            @Override public boolean areAllItemsEnabled() { return false; }
            @Override public boolean isEnabled(int position) { return availables.get(position); }
        };
        builder.setAdapter(adapter, (dialog, which) -> {
            BarcodeScannerFactory.ScannerType type = typeList.get(which);
            BarcodeScanner s = BarcodeScannerFactory.createScanner(type);
            if (!s.isAvailable(this)) {
                Toast.makeText(this, "当前设备不可用该方案", Toast.LENGTH_SHORT).show();
                return;
            }
            saveSelectedScannerType(type);
            barcodeScanner = s;
            updateTitleLabel();
            Toast.makeText(this, "已切换到：" + s.getName(), Toast.LENGTH_SHORT).show();
        });
        builder.show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 扫码页内切换也写入同一偏好；回到主界面必须显示用户最后选中的方案。
        syncSelectedScanner();
    }

    private void syncSelectedScanner() {
        BarcodeScannerFactory.ScannerType savedType = loadSelectedScannerType();
        BarcodeScanner candidate = savedType == null ? null : BarcodeScannerFactory.createScanner(savedType);
        if (candidate == null || !candidate.isAvailable(this)) {
            savedType = BarcodeScannerFactory.ScannerType.ML_KIT_STANDALONE;
            candidate = BarcodeScannerFactory.createScanner(savedType);
            saveSelectedScannerType(savedType);
        }
        barcodeScanner = candidate;
        updateTitleLabel();
    }

    /** 引擎胶囊显示当前扫码方案，更新时使用轻量文字过渡。 */
    private void updateTitleLabel() {
        if (textEngineLabel == null || barcodeScanner == null) return;
        textEngineLabel.animate().cancel();
        textEngineLabel.setAlpha(0f);
        textEngineLabel.setTranslationY(-3f);
        textEngineLabel.setText(barcodeScanner.getName());
        textEngineLabel.animate().alpha(1f).translationY(0f).setDuration(160).start();
    }

    private void saveSelectedScannerType(BarcodeScannerFactory.ScannerType type) {
        SharedPreferences sp = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        sp.edit().putString(KEY_SCANNER_TYPE, type.name()).apply();
    }

    private BarcodeScannerFactory.ScannerType loadSelectedScannerType() {
        SharedPreferences sp = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String name = sp.getString(KEY_SCANNER_TYPE, null);
        if (name == null) return null;
        try {
            return BarcodeScannerFactory.ScannerType.valueOf(name);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean checkCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestCameraPermission() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.CAMERA},
                PERMISSION_REQUEST_CAMERA);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CAMERA) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startScan();
            } else {
                // 如果用户拒绝了权限，并且勾选了"不再询问"（shouldShowRequestPermissionRationale返回false）
                // 或者只是拒绝了（返回true，但这里我们统一处理，如果是false则引导去设置）
                if (!ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {
                    showPermissionSettingsDialog();
                } else {
                    Toast.makeText(this, "需要相机权限才能扫码", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private void showPermissionSettingsDialog() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("需要相机权限")
                .setMessage("扫码功能需要访问相机。请在设置中开启相机权限。")
                .setPositiveButton("去设置", (dialog, which) -> {
                    try {
                        Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        Uri uri = Uri.fromParts("package", getPackageName(), null);
                        intent.setData(uri);
                        startActivity(intent);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void startScan() {
        if (barcodeScanner != null) {
            barcodeScanner.startScan(this, new BarcodeScanner.ScanCallback() {
                @Override
                public void onSuccess(String result) {
                    runOnUiThread(() -> {
                        if (result != null && !result.isEmpty()) {
                            // 文本、码制和引擎通过 Activity Result 带回，统一在结果面板中展示。
                        } else {
                            Toast.makeText(MainActivity.this, "扫描结果为空", Toast.LENGTH_SHORT).show();
                        }
                    });
                }

                @Override
                public void onCancel() {
                    // 返回即退出取景，不打断用户当前操作。
                }

                @Override
                public void onError(String error) {
                    runOnUiThread(() -> 
                        Toast.makeText(MainActivity.this, "扫描失败: " + error, Toast.LENGTH_SHORT).show()
                    );
                }
            });
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // 扫码页尚未迁移引擎接口的 Activity Result 启动方式，仅在这里保留桥接。
        if (requestCode == 2 && resultCode == RESULT_OK && data != null) {
            String content = data.getStringExtra("scan_text");
            if (content != null) {
                editTextUrl.setText(content);
                dbHelper.addHistoryItem(content, "scan");
                showScanResult(content, data.getStringExtra("scan_format"), data.getStringExtra("scan_engine"));
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }


    private void showScanResult(String content, String format, String engine) {
        com.google.android.material.bottomsheet.BottomSheetDialog dialog =
                new com.google.android.material.bottomsheet.BottomSheetDialog(this, R.style.ThemeOverlay_ProScan_BottomSheet);
        android.view.View view = getLayoutInflater().inflate(R.layout.bottom_sheet_scan_result, null);
        dialog.setContentView(view);
        ((TextView) view.findViewById(R.id.result_content)).setText(content);
        ((TextView) view.findViewById(R.id.result_format)).setText(
                format == null || format.isEmpty() ? getString(R.string.unknown_format) : format);
        ((TextView) view.findViewById(R.id.result_engine)).setText(
                engine == null || engine.isEmpty() ? barcodeScanner.getName() : engine);
        Button primary = view.findViewById(R.id.button_primary_result);
        final boolean webLink = content.matches("^https?://\\S+$");
        primary.setText(webLink ? R.string.open_link : R.string.copy_content);
        primary.setOnClickListener(v -> {
            if (webLink) {
                try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(content))); }
                catch (Exception e) { Toast.makeText(this, "无法打开链接", Toast.LENGTH_SHORT).show(); }
            } else {
                copyToClipboard(content);
                dialog.dismiss();
            }
        });
        view.findViewById(R.id.button_copy_result).setOnClickListener(v -> {
            copyToClipboard(content);
            dialog.dismiss();
        });
        view.findViewById(R.id.button_done_result).setOnClickListener(v -> dialog.dismiss());
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            int bottom = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars()).bottom;
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(),
                    getResources().getDimensionPixelSize(R.dimen.space_20) + bottom);
            return insets;
        });
        dialog.setOnShowListener(d -> {
            android.view.Window window = dialog.getWindow();
            if (window != null) androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false);
            androidx.core.view.ViewCompat.requestApplyInsets(view);
        });
        dialog.show();
    }

    private void copyToClipboard(String content) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("扫描内容", content));
        Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show();
    }

    private void generateBarcode() {
        final String content = editTextUrl.getText().toString();
        if (content.isEmpty()) {
            Toast.makeText(this, "请输入内容", Toast.LENGTH_SHORT).show();
            return;
        }
        // 检查内容是否只包含合法字符（条形码仅支持ASCII）
        if (!content.matches("^[\\x00-\\x7F]+$")) {
            Toast.makeText(this, "条形码内容只能包含ASCII字符", Toast.LENGTH_SHORT).show();
            return;
        }

        // 生成位图放到后台线程，避免主线程卡顿/ANR
        showGeneratingDialog();
        executor.execute(() -> {
            try {
                MultiFormatWriter writer = new MultiFormatWriter();
                // 使用屏幕高度作为宽度，屏幕宽度作为高度，以便旋转后充分利用屏幕空间
                int screenWidth = getResources().getDisplayMetrics().widthPixels;
                int screenHeight = getResources().getDisplayMetrics().heightPixels;
                BitMatrix bitMatrix = writer.encode(content, BarcodeFormat.CODE_128, screenHeight, screenWidth);

                // 创建位图并旋转90度
                Bitmap bitmap = Bitmap.createBitmap(screenWidth, screenHeight, Bitmap.Config.ARGB_8888);
                for (int x = 0; x < screenHeight; x++) {
                    for (int y = 0; y < screenWidth; y++) {
                        bitmap.setPixel(y, x, bitMatrix.get(x, y) ? Color.BLACK : Color.WHITE);
                    }
                }

                // 旋转90度
                Matrix matrix = new Matrix();
                matrix.postRotate(90);
                Bitmap rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
                bitmap.recycle();

                runOnUiThread(() -> {
                    dismissGeneratingDialog();
                    showImageDialog(rotatedBitmap);
                });
            } catch (WriterException e) {
                runOnUiThread(() -> {
                    dismissGeneratingDialog();
                    Toast.makeText(this, "生成条形码失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    dismissGeneratingDialog();
                    Toast.makeText(this, "生成条形码时发生错误", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void generateQRCode() {
        final String content = editTextUrl.getText().toString();
        if (content.isEmpty()) {
            Toast.makeText(this, "请输入内容", Toast.LENGTH_SHORT).show();
            return;
        }
        // 检查内容长度
        if (content.length() > 3000) {
            Toast.makeText(this, "二维码内容过长，请控制在3000字符以内", Toast.LENGTH_SHORT).show();
            return;
        }

        // 生成位图放到后台线程，避免主线程卡顿/ANR
        showGeneratingDialog();
        executor.execute(() -> {
            try {
                MultiFormatWriter writer = new MultiFormatWriter();
                // 使用屏幕宽度作为二维码尺寸
                int size = getResources().getDisplayMetrics().widthPixels;
                BitMatrix bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size);
                Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);

                for (int x = 0; x < size; x++) {
                    for (int y = 0; y < size; y++) {
                        bitmap.setPixel(x, y, bitMatrix.get(x, y) ? Color.BLACK : Color.WHITE);
                    }
                }

                runOnUiThread(() -> {
                    dismissGeneratingDialog();
                    showImageDialog(bitmap);
                });
            } catch (WriterException e) {
                runOnUiThread(() -> {
                    dismissGeneratingDialog();
                    Toast.makeText(this, "生成二维码失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    dismissGeneratingDialog();
                    Toast.makeText(this, "生成二维码时发生错误", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    /** 统一的码图展示对话框（二维码/条形码共用） */
    private void showImageDialog(Bitmap bitmap) {
        try {
            Dialog dialog = new Dialog(this, android.R.style.Theme_Material_Light_NoActionBar);
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

            // 创建白色背景的ImageView
            ImageView imageView = new ImageView(this);
            imageView.setBackgroundColor(Color.WHITE);
            imageView.setImageBitmap(bitmap);

            // 设置边距为屏幕宽度的2.5%
            int margin = (int) (getResources().getDisplayMetrics().widthPixels * 0.025);
            imageView.setPadding(margin, margin, margin, margin);

            dialog.setContentView(imageView);
            dialog.getWindow().setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            );

            imageView.setOnClickListener(v -> {
                dialog.dismiss();
                bitmap.recycle();
            });
            dialog.setOnDismissListener(d -> {
                if (!bitmap.isRecycled()) bitmap.recycle();
            });
            dialog.show();
        } catch (Exception e) {
            Toast.makeText(this, "显示码图时发生错误", Toast.LENGTH_SHORT).show();
            bitmap.recycle();
        }
    }

    private void showGeneratingDialog() {
        generatingDialog = new Dialog(this);
        generatingDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        TextView tv = new TextView(this);
        tv.setText("生成中…");
        tv.setPadding(48, 32, 48, 32);
        generatingDialog.setContentView(tv);
        generatingDialog.setCancelable(false);
        generatingDialog.show();
    }

    private void dismissGeneratingDialog() {
        if (generatingDialog != null && generatingDialog.isShowing()) {
            generatingDialog.dismiss();
        }
        generatingDialog = null;
    }
}
