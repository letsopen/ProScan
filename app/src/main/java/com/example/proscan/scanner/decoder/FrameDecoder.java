package com.example.proscan.scanner.decoder;

import android.graphics.Bitmap;

import androidx.camera.core.ImageProxy;

/**
 * 相机帧/位图解码器统一接口
 * 由 UnifiedScanActivity 按当前引擎分派调用，所有实现必须可在相机分析线程上同步调用
 */
public interface FrameDecoder {

    /**
     * 解码一帧相机图像（YUV_420_888）
     * @param frame CameraX 图像分析帧，实现内部负责 close()
     * @return 解码结果文本，无结果返回 null
     */
    String decode(ImageProxy frame);

    /**
     * 解码一张位图（相册识别等场景）
     * @param bitmap 待识别位图
     * @return 解码结果文本，无结果返回 null
     */
    String decodeBitmap(Bitmap bitmap);

    /** 释放持有的资源（scanner 实例、缓冲区等） */
    void close();
}
