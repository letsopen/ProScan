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
import com.example.proscan.scanner.impl.HuaweiBarcodeScanner;
import com.example.proscan.scanner.impl.ZxingBarcodeScanner;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.app.Dialog;
import android.widget.ImageView;
import android.view.Window;
import android.view.ViewGroup;
import android.graphics.Matrix;
import android.content.SharedPreferences;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;

public class MainActivity extends AppCompatActivity {
    private EditText editTextUrl;
    private Button buttonScan;
    private Button buttonPaste;
    private Button btnVisit;
    private ImageButton buttonHistory;
    private TextView textTitle;
    private HistoryDbHelper dbHelper;
    private BarcodeScanner barcodeScanner;
    private static final String PREFS = "proscan_prefs";
    private static final String KEY_SCANNER_TYPE = "scanner_type";

    private static final int PERMISSION_REQUEST_CAMERA = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        dbHelper = new HistoryDbHelper(this);
        editTextUrl = findViewById(R.id.editTextUrl);
        buttonScan = findViewById(R.id.buttonScan);
        buttonPaste = findViewById(R.id.buttonPaste);
        btnVisit = findViewById(R.id.btnVisit);
        buttonHistory = findViewById(R.id.buttonHistory);
        textTitle = findViewById(R.id.textTitle);

        BarcodeScannerFactory.ScannerType savedType = loadSelectedScannerType();
        if (savedType != null) {
            BarcodeScanner candidate = BarcodeScannerFactory.createScanner(savedType);
            if (candidate.isAvailable(this)) {
                barcodeScanner = candidate;
            } else {
                barcodeScanner = BarcodeScannerFactory.getAvailableScanner(this);
            }
        } else {
            barcodeScanner = BarcodeScannerFactory.getAvailableScanner(this);
        }

        textTitle.setOnClickListener(v -> showScannerChoiceDialog());

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
                editTextUrl.setText(text);
                dbHelper.addHistoryItem(text, "paste");
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
            Intent intent = new Intent(this, HistoryActivity.class);
            startActivityForResult(intent, 1);
        });

        Button btnBarcode = findViewById(R.id.btnBarcode);
        Button btnQRCode = findViewById(R.id.btnQRCode);

        btnBarcode.setOnClickListener(v -> generateBarcode());
        btnQRCode.setOnClickListener(v -> generateQRCode());
    }

    private void showScannerChoiceDialog() {
        BarcodeScannerFactory.ScannerType[] types = BarcodeScannerFactory.ScannerType.values();
        String[] names = new String[types.length];
        for (int i = 0; i < types.length; i++) {
            BarcodeScanner s = BarcodeScannerFactory.createScanner(types[i]);
            boolean available = s.isAvailable(this);
            names[i] = s.getName() + (available ? "" : "（不可用）");
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("选择解码方案");
        builder.setItems(names, (dialog, which) -> {
            BarcodeScannerFactory.ScannerType type = types[which];
            BarcodeScanner s = BarcodeScannerFactory.createScanner(type);
            if (!s.isAvailable(this)) {
                Toast.makeText(this, "当前设备不可用该方案", Toast.LENGTH_SHORT).show();
                return;
            }
            saveSelectedScannerType(type);
            barcodeScanner = s;
            Toast.makeText(this, "已切换到：" + s.getName(), Toast.LENGTH_SHORT).show();
        });
        builder.show();
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
                            editTextUrl.setText("");
                            editTextUrl.setText(result);
                            dbHelper.addHistoryItem(result, "scan");
                            Toast.makeText(MainActivity.this, "扫描成功", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(MainActivity.this, "扫描结果为空", Toast.LENGTH_SHORT).show();
                        }
                    });
                }

                @Override
                public void onCancel() {
                    runOnUiThread(() -> 
                        Toast.makeText(MainActivity.this, "扫描已取消", Toast.LENGTH_SHORT).show()
                    );
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
        // 处理华为扫码结果（华为使用Activity Result）
        if (HuaweiBarcodeScanner.handleActivityResult(requestCode, resultCode, data)) {
            // 华为已处理，返回
            return;
        }
        
        // 处理ZXing扫码结果（ZXing使用Activity Result）
        if (ZxingBarcodeScanner.handleActivityResult(requestCode, resultCode, data)) {
            // ZXing已处理，返回
            return;
        }
        
        // 处理从历史记录返回的内容
        if (requestCode == 1 && resultCode == RESULT_OK && data != null) {
            String content = data.getStringExtra("content");
            if (content != null) {
                editTextUrl.setText(content);
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void generateBarcode() {
        String content = editTextUrl.getText().toString();
        if (content.isEmpty()) {
            Toast.makeText(this, "请输入内容", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            // 检查内容是否只包含合法字符
            if (!content.matches("^[\\x00-\\x7F]+$")) {
                Toast.makeText(this, "条形码内容只能包含ASCII字符", Toast.LENGTH_SHORT).show();
                return;
            }

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
            
            showBarcodeDialog(rotatedBitmap);
        } catch (WriterException e) {
            Toast.makeText(this, "生成条形码失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "生成条形码时发生错误", Toast.LENGTH_SHORT).show();
        }
    }

    private void generateQRCode() {
        String content = editTextUrl.getText().toString();
        if (content.isEmpty()) {
            Toast.makeText(this, "请输入内容", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            // 检查内容长度
            if (content.length() > 3000) {
                Toast.makeText(this, "二维码内容过长，请控制在3000字符以内", Toast.LENGTH_SHORT).show();
                return;
            }

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
            
            showQRCodeDialog(bitmap);
        } catch (WriterException e) {
            Toast.makeText(this, "生成二维码失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "生成二维码时发生错误", Toast.LENGTH_SHORT).show();
        }
    }

    private void showBarcodeDialog(Bitmap bitmap) {
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
            dialog.show();
        } catch (Exception e) {
            Toast.makeText(this, "显示条形码时发生错误", Toast.LENGTH_SHORT).show();
            bitmap.recycle();
        }
    }

    private void showQRCodeDialog(Bitmap bitmap) {
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
            dialog.show();
        } catch (Exception e) {
            Toast.makeText(this, "显示二维码时发生错误", Toast.LENGTH_SHORT).show();
            bitmap.recycle();
        }
    }
} 
