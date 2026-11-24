package com.kset.image.generation;

import java.util.List;

/**
 * 图片生成结果：images 为本次生成的全部图片。
 */
public record ImageGenerationResult(List<GeneratedImage> images) {

    public ImageGenerationResult {
        if (images == null || images.isEmpty()) {
            throw new IllegalArgumentException("images 不能为空");
        }
        images = List.copyOf(images);
    }
}
