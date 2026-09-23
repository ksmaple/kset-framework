package com.kset.agent.spi;

import java.util.List;

/**
 * 已登记代码仓库查询端口。
 */
public interface CodeRepositoryAccessPort {

    /**
     * 列出全部已登记仓库；无代码仓库管理能力时返回空列表。
     */
    List<CodeRepositoryRef> findAll();
}
