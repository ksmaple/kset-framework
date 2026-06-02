package com.kset.auth.spi;

import com.kset.common.auth.LoginUser;

public interface PermissionChecker {

    boolean hasRole(LoginUser user, String subjectType, String role);

    boolean hasPermission(LoginUser user, String subjectType, String permission);
}
