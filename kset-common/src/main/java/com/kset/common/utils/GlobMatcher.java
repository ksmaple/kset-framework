package com.kset.common.utils;

import java.nio.file.FileSystems;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;

/**
 * Glob 模式匹配工具
 */
public class GlobMatcher {

    private GlobMatcher() {
    }

    
    public static boolean matches(String path, String patterns) {
        if (path == null || patterns == null || patterns.isBlank()) {
            return false;
        }
        String[] parts = patterns.split(",");
        for (String part : parts) {
            String pattern = part.trim();
            if (pattern.isEmpty()) {
                continue;
            }
            // 支持以 / 结尾的目录匹配：如 .git/ 匹配 .git 目录下所有内容
            if (pattern.endsWith("/")) {
                String dirPrefix = pattern.substring(0, pattern.length() - 1);
                if (path.contains("/" + dirPrefix + "/") || path.startsWith(dirPrefix + "/")) {
                    return true;
                }
            }
            try {
                String glob = "glob:" + pattern;
                PathMatcher matcher = FileSystems.getDefault().getPathMatcher(glob);
                if (matcher.matches(Paths.get(path))) {
                    return true;
                }
            } catch (Exception e) {
                continue;
            }
        }
        return false;
    }
}
