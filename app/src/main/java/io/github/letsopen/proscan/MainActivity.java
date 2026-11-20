package io.github.letsopen.proscan;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import io.github.letsopen.proscan.generator.CodeGeneratorActivity;
import io.github.letsopen.proscan.generator.CodeType;

import android.Manifest;
import android.content.pm.PackageManager;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import androidx.appcompat.app.AppCompatActivity;

import io.github.letsopen.proscan.db.HistoryDbHelper;
import io.github.letsopen.proscan.scanner.BarcodeScanner;
import io.github.letsopen.proscan.scanner.BarcodeScannerFactory;

// 生成功能仅用 zxing-core；识别由 ML Kit、HMS 和 ZXing-CPP 完成

import android.app.Dialog;
import android.view.Window;

import android.content.SharedPreferences;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;


import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private EditText editTextUrl;
    private Button buttonScan;
    private Button buttonPaste;
    private Button btnVisit;
    private Button buttonHistory;
    private HistoryDbHelper dbHelper;
    private BarcodeScanner barcodeScanner;
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

        // 新用户、无有效偏好或上次选择已不可用时，默认 GOOGLE ML。
        syncSelectedScanner();

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

        btnBarcode.setOnClickListener(v -> openCodeGenerator(CodeType.BARCODE));
        btnQRCode.setOnClickListener(v -> openCodeGenerator(CodeType.QR));
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


    @Override
    protected void onResume() {
        super.onResume();
        // 扫码页内切换仍写回同一偏好，主界面仅同步实际使用的引擎，不再展示选择器。
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

    private void openCodeGenerator(CodeType type) {
        String content = editTextUrl.getText().toString().trim();
        if (content.isEmpty()) {
            Toast.makeText(this, "请输入内容", Toast.LENGTH_SHORT).show();
            return;
        }
        if (type == CodeType.QR && content.length() > 3000) {
            Toast.makeText(this, "二维码内容过长，请控制在3000字符以内", Toast.LENGTH_SHORT).show();
            return;
        }
        if (type == CodeType.BARCODE) {
            String validationError = io.github.letsopen.proscan.generator.BarcodeInputValidator
                    .validate(content, "CODE128");
            if (validationError != null) {
                Toast.makeText(this, validationError, Toast.LENGTH_LONG).show();
                return;
            }
        }
        Intent intent = new Intent(this, CodeGeneratorActivity.class);
        intent.putExtra(CodeGeneratorActivity.EXTRA_CONTENT, content);
        intent.putExtra(CodeGeneratorActivity.EXTRA_TYPE, type.name());
        startActivity(intent);
    }

}
