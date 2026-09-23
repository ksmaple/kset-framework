package com.kset.agent.spi;

import java.util.Set;

/**
 * 文档可读性查询端口（Agent 输出资源引用校验用）。
 */
public interface DocumentAccessPort {

    /**
     * 当前用户可读文档 ID 集合；返回 {@code null} 表示不启用文档级过滤（如超管或未接入文档体系）。
     */
    Set<Long> resolveReadableDocumentIds();
}
