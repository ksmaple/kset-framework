package com.kset.auth.spi;

import com.kset.common.auth.LoginUser;

import java.util.Map;
import java.util.Optional;

public interface LoginUserHeaderCodec {

    Map<String, String> encode(LoginUser user, boolean includeToken);

    Optional<LoginUser> decode(HeaderReader reader);

    default Map<String, String> encode(LoginUser user, boolean includeToken, String subjectType) {
        LoginUser targetUser = user != null && subjectType != null && !subjectType.isBlank()
                ? user.withSubjectType(subjectType)
                : user;
        return encode(targetUser, includeToken);
    }

    default Optional<LoginUser> decode(HeaderReader reader, String subjectType, String headerName) {
        return decode(reader)
                .map(user -> subjectType != null && !subjectType.isBlank() ? user.withSubjectType(subjectType) : user);
    }

    interface HeaderReader {
        String get(String name);
    }
}
