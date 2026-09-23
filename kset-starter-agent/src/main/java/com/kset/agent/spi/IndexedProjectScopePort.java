package com.kset.agent.spi;

import java.util.List;

/**
 * 可访问已索引项目查询端口（用于编排时构造代码检索范围提示）。
 */
public interface IndexedProjectScopePort {

    /**
     * 当前用户可访问且已建立索引的项目列表；无代码检索能力时返回空列表。
     */
    List<ProjectScopeItem> listAccessibleIndexedProjects();
}
