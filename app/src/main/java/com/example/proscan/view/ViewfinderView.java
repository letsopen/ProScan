package com.example.proscan.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * 扫码遮罩层 View
 * 绘制半透明背景和中间透明的取景框
 */
public class ViewfinderView extends View {
    private Paint maskPaint;
    private Paint framePaint;
    private Paint cornerPaint;
    private RectF frameRect;
    private int maskColor = Color.parseColor("#80000000"); // 半透明黑色
    private int frameColor = Color.WHITE;
    private int cornerWidth = 10;
    private int cornerLength = 60;
    
    public ViewfinderView(Context context) {
        super(context);
        init();
    }

    public ViewfinderView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ViewfinderView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        maskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        maskPaint.setColor(maskColor);

        framePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        framePaint.setColor(frameColor);
        framePaint.setStyle(Paint.Style.STROKE);
        framePaint.setStrokeWidth(4);

        cornerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        cornerPaint.setColor(frameColor);
        cornerPaint.setStyle(Paint.Style.STROKE);
        cornerPaint.setStrokeWidth(12);
        
        frameRect = new RectF();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        int width = getWidth();
        int height = getHeight();
        
        // 计算中间取景框的大小（屏幕宽度的 70%）
        int frameSize = (int) (Math.min(width, height) * 0.7);
        int left = (width - frameSize) / 2;
        int top = (height - frameSize) / 2;
        int right = left + frameSize;
        int bottom = top + frameSize;
        
        frameRect.set(left, top, right, bottom);
        
        // 1. 绘制半透明遮罩（除了中间矩形）
        // 这里使用 layer save 来处理镂空效果
        int layerId = canvas.saveLayer(0, 0, width, height, null);
        
        // 绘制全屏半透明遮罩
        canvas.drawColor(maskColor);
        
        // 设置混合模式为 Clear，用于"挖空"中间部分
        maskPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        canvas.drawRect(frameRect, maskPaint);
        maskPaint.setXfermode(null); // 还原
        
        canvas.restoreToCount(layerId);
        
        // 2. 绘制取景框边框（细线）
        canvas.drawRect(frameRect, framePaint);
        
        // 3. 绘制四个角的粗线
        // 左上角
        canvas.drawLine(left - 6, top, left + cornerLength, top, cornerPaint);
        canvas.drawLine(left, top - 6, left, top + cornerLength, cornerPaint);
        
        // 右上角
        canvas.drawLine(right - cornerLength, top, right + 6, top, cornerPaint);
        canvas.drawLine(right, top - 6, right, top + cornerLength, cornerPaint);
        
        // 左下角
        canvas.drawLine(left - 6, bottom, left + cornerLength, bottom, cornerPaint);
        canvas.drawLine(left, bottom - cornerLength, left, bottom + 6, cornerPaint);
        
        // 右下角
        canvas.drawLine(right - cornerLength, bottom, right + 6, bottom, cornerPaint);
        canvas.drawLine(right, bottom - cornerLength, right, bottom + 6, cornerPaint);
    }
}
