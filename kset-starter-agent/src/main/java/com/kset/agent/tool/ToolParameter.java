package com.kset.agent.tool;

import java.util.Map;

/**
 * 工具参数定义
 *
 * @param name        参数名
 * @param type        参数类型：string / integer / number / boolean / array / object
 * @param description 参数描述
 * @param required    是否必填
 * @param defaultValue 默认值（可选）
 */
public record ToolParameter(String name, String type, String description, boolean required, Object defaultValue) {
}
