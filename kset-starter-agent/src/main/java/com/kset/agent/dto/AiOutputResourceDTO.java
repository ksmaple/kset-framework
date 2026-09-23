package com.kset.agent.dto;

import lombok.Data;

import java.util.Date;

@Data
public class AiOutputResourceDTO {
    private String resourceType;

    private String resourceId;

    private Long documentId;

    private String name;

    private String mimeType;

    private String url;

    private String previewUrl;

    private String description;

    private Long fileSize;

    private Integer width;

    private Integer height;

    private String accessMode;

    private Date expiresAt;
}
