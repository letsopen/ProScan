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

public class MainActivity extends AppCompatActivity {
    private EditText editTextUrl;
    private Button buttonScan;
    private Button buttonPaste;
    private Button btnVisit;
    private ImageButton buttonHistory;
    private HistoryDbHelper dbHelper;
    private BarcodeScanner barcodeScanner;

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

        // 初始化扫码器（优先使用Google ML Kit，如果不可用则使用ZXing）
        barcodeScanner = BarcodeScannerFactory.getAvailableScanner(this);

        buttonScan.setOnClickListener(v -> {
            if (barcodeScanner != null) {
                barcodeScanner.startScan(this, new BarcodeScanner.ScanCallback() {
                    @Override
                    public void onSuccess(String result) {
                        editTextUrl.setText(result);
                        dbHelper.addHistoryItem(result, "scan");
                        Toast.makeText(MainActivity.this, "扫描成功", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onCancel() {
                        Toast.makeText(MainActivity.this, "扫描已取消", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onError(String error) {
                        Toast.makeText(MainActivity.this, "扫描失败: " + error, Toast.LENGTH_SHORT).show();
                    }
                });
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