package io.github.letsopen.proscan.generator;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.provider.MediaStore;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

/** PNG 导出与分享。高清位图由调用方在后台线程持有，此处不回收它。 */
public final class CodeImageExporter {
    private CodeImageExporter() {}

    public static Uri saveToGallery(Context context, Bitmap bitmap, String name) throws Exception {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, name + ".png");
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
        values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ProScan");
        values.put(MediaStore.Images.Media.IS_PENDING, 1);
        Uri uri = context.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        if (uri == null) throw new IllegalStateException("无法创建图片记录");
        try (OutputStream output = context.getContentResolver().openOutputStream(uri)) {
            if (output == null || !bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                throw new IllegalStateException("图片写入失败");
            }
        } catch (Exception e) {
            context.getContentResolver().delete(uri, null, null);
            throw e;
        }
        values.clear();
        values.put(MediaStore.Images.Media.IS_PENDING, 0);
        context.getContentResolver().update(uri, values, null, null);
        return uri;
    }

    public static void share(Context context, Bitmap bitmap, String name) throws Exception {
        File directory = new File(context.getCacheDir(), "generated");
        if (!directory.exists() && !directory.mkdirs()) throw new IllegalStateException("无法创建分享目录");
        File file = new File(directory, name + ".png");
        try (FileOutputStream output = new FileOutputStream(file)) {
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) throw new IllegalStateException("分享图片写入失败");
        }
        Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", file);
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("image/png");
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        context.startActivity(Intent.createChooser(intent, "分享码图"));
    }
}
