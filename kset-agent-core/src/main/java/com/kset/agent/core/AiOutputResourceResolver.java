package com.kset.agent.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kset.agent.core.dto.AiOutputResourceDTO;
import com.kset.agent.core.spi.DocumentAccessPort;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class AiOutputResourceResolver {

    private static final Set<String> SUPPORTED_TYPES = Set.of("IMAGE", "DOCUMENT", "FILE");
    private static final Set<String> ACCESS_MODES = Set.of("PREVIEW", "DOWNLOAD", "BOTH");

    private final ObjectMapper objectMapper;
    private final DocumentAccessPort documentPermissionService;

    public AiOutputResourceResolver(ObjectMapper objectMapper,
                                    DocumentAccessPort documentPermissionService) {
        this.objectMapper = objectMapper;
        this.documentPermissionService = documentPermissionService;
    }

    public Resolution resolve(List<String> refs, List<Map<String, Object>> observations, int maxCount) {
        if (refs == null || refs.isEmpty()) {
            return new Resolution(List.of(), null);
        }
        if (refs.size() > maxCount) {
            return new Resolution(List.of(), "final_answer.resourceRefs 最多包含 " + maxCount + " 个引用");
        }
        Map<String, AiOutputResourceDTO> available = collectResources(observations);
        // 本次调用内复用一次批量可读文档集合（null 表示超管不过滤），避免逐条权限判定
        Set<Long> readableDocIds = documentPermissionService.resolveReadableDocumentIds();
        List<AiOutputResourceDTO> resolved = new ArrayList<>();
        Set<String> unique = new LinkedHashSet<>();
        for (String ref : refs) {
            String normalized = ref == null ? "" : ref.trim();
            if (normalized.isEmpty() || !unique.add(normalized)) {
                return new Resolution(List.of(), "resourceRefs 必须非空且唯一");
            }
            AiOutputResourceDTO resource = available.get(normalized);
            if (resource == null) {
                return new Resolution(List.of(), "resourceRefs 包含工具结果中不存在的资源: " + normalized);
            }
            String error = validate(resource, readableDocIds);
            if (error != null) {
                return new Resolution(List.of(), error);
            }
            resolved.add(resource);
        }
        return new Resolution(List.copyOf(resolved), null);
    }

    private Map<String, AiOutputResourceDTO> collectResources(List<Map<String, Object>> observations) {
        Map<String, AiOutputResourceDTO> resources = new LinkedHashMap<>();
        if (observations == null) {
            return resources;
        }
        for (Map<String, Object> observation : observations) {
            collectFromValue(observation.get("evidenceItems"), resources);
        }
        return resources;
    }

    private void collectFromValue(Object value, Map<String, AiOutputResourceDTO> resources) {
        if (value instanceof List<?> values) {
            values.forEach(item -> collectFromValue(item, resources));
            return;
        }
        if (!(value instanceof Map<?, ?> source)) {
            return;
        }
        Object nested = source.get("resources");
        if (nested != null) {
            collectResourceList(nested, resources);
        }
        source.values().forEach(item -> {
            if (item instanceof Map<?, ?> || item instanceof List<?>) {
                collectFromValue(item, resources);
            }
        });
    }

    private void collectResourceList(Object value, Map<String, AiOutputResourceDTO> resources) {
        if (!(value instanceof List<?> values)) {
            return;
        }
        for (Object item : values) {
            if (!(item instanceof Map<?, ?>)) {
                continue;
            }
            AiOutputResourceDTO resource = objectMapper.convertValue(item, AiOutputResourceDTO.class);
            if (resource.getResourceId() != null && !resource.getResourceId().isBlank()) {
                resources.putIfAbsent(resource.getResourceId().trim(), resource);
            }
        }
    }

    private String validate(AiOutputResourceDTO resource, Set<Long> readableDocIds) {
        String type = normalize(resource.getResourceType());
        if (!SUPPORTED_TYPES.contains(type)) {
            return "资源类型必须是 IMAGE、DOCUMENT 或 FILE";
        }
        if (resource.getName() == null || resource.getName().isBlank()) {
            return "资源名称不能为空";
        }
        if (resource.getDocumentId() != null && readableDocIds != null
                && !readableDocIds.contains(resource.getDocumentId())) {
            return "无权访问资源对应的系统文档";
        }
        String accessMode = normalize(resource.getAccessMode());
        if (!ACCESS_MODES.contains(accessMode)) {
            return "资源访问方式必须是 PREVIEW、DOWNLOAD 或 BOTH";
        }
        if (!allowedUrl(resource.getUrl()) || !allowedUrl(resource.getPreviewUrl())) {
            return "资源地址只允许系统 /api/ 路径或工具签发的 HTTPS 地址";
        }
        resource.setResourceType(type);
        resource.setAccessMode(accessMode);
        return null;
    }

    private boolean allowedUrl(String url) {
        if (url == null || url.isBlank()) {
            return true;
        }
        String normalized = url.trim().toLowerCase(Locale.ROOT);
        return normalized.startsWith("/api/") || normalized.startsWith("https://");
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    public record Resolution(List<AiOutputResourceDTO> resources, String error) {
        public boolean valid() {
            return error == null;
        }
    }
}
