package com.kset.auth.core;

import com.kset.auth.spi.PermissionChecker;
import com.kset.common.auth.LoginUser;

public class DefaultPermissionChecker implements PermissionChecker {

    @Override
    public boolean hasRole(LoginUser user, String subjectType, String role) {
        return user != null && user.isSubject(subjectType) && user.getRoles().contains(role);
    }

    @Override
    public boolean hasPermission(LoginUser user, String subjectType, String permission) {
        return user != null && user.isSubject(subjectType) && user.getPermissions().contains(permission);
    }
}
