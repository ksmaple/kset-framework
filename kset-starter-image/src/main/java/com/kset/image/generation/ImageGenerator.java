package com.kset.image.generation;

import com.kset.image.ImageException;

/**
 * 图片生成入口：按画面描述生成图片，或通过所选供应商支持的协议处理参考图。
 */
public interface ImageGenerator {

    /**
     * 生成图片。
     *
     * @param request 画面描述、尺寸与数量
     * @return 生成图片列表
     * @throws ImageException 网络错误、供应商拒绝或响应无法解析
     */
    ImageGenerationResult generate(ImageGenerationRequest request);
}
