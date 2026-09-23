package com.kset.agent.dto;

import com.kset.agent.tool.ToolEvidenceLevel;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具执行后的通用证据观察。
 */
public record ToolObservation(
        String toolName,
        Map<String, Object> arguments,
        boolean success,
        Boolean hasEvidence,
        String resourceType,
        String operation,
        String evidenceLevel,
        String cost,
        String resultSchema,
        Object result,
        List<Map<String, Object>> evidenceItems,
        List<String> nextCapabilities,
        String errorCode,
        String error) {

    public ToolObservation {
        hasEvidence = hasEvidence != null
                ? success && hasEvidence
                : success && !ToolEvidenceLevel.NONE.code().equalsIgnoreCase(evidenceLevel);
        arguments = arguments != null
                ? Collections.unmodifiableMap(new LinkedHashMap<>(arguments))
                : Map.of();
        evidenceItems = evidenceItems != null ? List.copyOf(evidenceItems) : List.of();
        nextCapabilities = nextCapabilities != null ? List.copyOf(nextCapabilities) : List.of();
    }

    public String resultText() {
        return result != null ? String.valueOf(result) : "";
    }

    public Map<String, Object> toContextMap() {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("toolName", toolName);
        context.put("arguments", arguments);
        context.put("success", success);
        context.put("hasEvidence", hasEvidence);
        context.put("resourceType", resourceType);
        context.put("operation", operation);
        context.put("evidenceLevel", evidenceLevel);
        context.put("cost", cost);
        context.put("resultSchema", resultSchema);
        context.put("result", result);
        context.put("evidenceItems", evidenceItems);
        context.put("nextCapabilities", nextCapabilities);
        if (errorCode != null && !errorCode.isBlank()) {
            context.put("errorCode", errorCode);
        }
        if (error != null && !error.isBlank()) {
            context.put("error", error);
        }
        return context;
    }
}
