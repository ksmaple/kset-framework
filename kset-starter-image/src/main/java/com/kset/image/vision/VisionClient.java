package com.kset.image.vision;

import com.kset.image.ImageException;

/**
 * 图片理解入口：将图片与提取指令交给多模态模型，返回文字描述或结构化参数。
 */
public interface VisionClient {

    /**
     * 理解图片内容。
     *
     * @param request 图片来源与理解指令
     * @return 原始回答与可选结构化参数
     * @throws ImageException 网络错误、供应商拒绝或响应无法解析
     */
    VisionResult understand(VisionRequest request);
}
