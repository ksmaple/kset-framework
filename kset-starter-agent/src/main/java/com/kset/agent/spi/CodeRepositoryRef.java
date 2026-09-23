package com.kset.agent.spi;

import lombok.Data;

/**
 * 已登记代码仓库引用（工具路径合法性校验用）。
 */
@Data
public class CodeRepositoryRef {
    private Long id;
    private Long projectId;
    private String path;

    public CodeRepositoryRef() {
    }

    public CodeRepositoryRef(Long id, Long projectId, String path) {
        this.id = id;
        this.projectId = projectId;
        this.path = path;
    }
}
