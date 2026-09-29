package com.kset.agent.core.spi;

/**
 * 项目存在性与成员访问校验端口。
 */
public interface ProjectAccessPort {

    /**
     * 项目是否存在（未删除）。
     */
    boolean projectExists(Long projectId);

    /**
     * 用户是否为项目的启用成员。
     */
    boolean isActiveMember(Long projectId, Long userId);

    /**
     * 当前用户是否可访问项目（工具执行期校验）。
     */
    boolean canAccessProject(Long projectId);
}
