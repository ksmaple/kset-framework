package com.kset.common.auth;

import com.kset.common.context.KsetContext;
import com.kset.common.context.KsetContextKeys;

import java.util.Optional;

public final class LoginContext {

    private LoginContext() {
    }

    public static Optional<LoginUser> currentUser() {
        return KsetContext.get(KsetContextKeys.LOGIN_USER);
    }

    public static LoginUser requireUser() {
        return currentUser().orElseThrow(() -> new LoginRequiredException("未登录"));
    }

    public static LoginUser requireUser(String subjectType) {
        LoginUser user = requireUser();
        if (!user.isSubject(subjectType)) {
            throw new LoginRequiredException("未登录");
        }
        return user;
    }

    public static Optional<String> currentUserId() {
        return currentUser().map(LoginUser::getUserId);
    }

    public static Optional<String> currentSubjectType() {
        return currentUser().map(LoginUser::getSubjectType);
    }

    public static boolean hasRole(String role) {
        return role != null && currentUser()
                .map(user -> user.getRoles().contains(role))
                .orElse(false);
    }

    public static boolean hasRole(String subjectType, String role) {
        return role != null && currentUser()
                .filter(user -> user.isSubject(subjectType))
                .map(user -> user.getRoles().contains(role))
                .orElse(false);
    }

    public static boolean hasPermission(String permission) {
        return permission != null && currentUser()
                .map(user -> user.getPermissions().contains(permission))
                .orElse(false);
    }

    public static boolean hasPermission(String subjectType, String permission) {
        return permission != null && currentUser()
                .filter(user -> user.isSubject(subjectType))
                .map(user -> user.getPermissions().contains(permission))
                .orElse(false);
    }

    public static void bind(LoginUser user) {
        if (user == null) {
            clear();
        } else {
            KsetContext.put(KsetContextKeys.LOGIN_USER, user);
        }
    }

    public static void clear() {
        KsetContext.remove(KsetContextKeys.LOGIN_USER);
    }

    public static LoginContextSnapshot capture() {
        return new LoginContextSnapshot(KsetContext.capture());
    }

    public static void restore(LoginContextSnapshot snapshot) {
        if (snapshot == null) {
            clear();
        } else {
            bind(snapshot.getUser());
        }
    }

    public static LoginContextScope openScope(LoginContextSnapshot snapshot) {
        LoginContextSnapshot previous = capture();
        restore(snapshot);
        return new LoginContextScope(previous);
    }
}
