package com.kset.redis.rank;

import com.kset.redis.rank.internal.KsetRedisRankKeys;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KsetRedisRankKeysTest {

    @Test
    void groupKeysShareOneClusterHashTag() {
        KsetRedisRankKeys keys = KsetRedisRankKeys.forRoot("myapp:rank:season-1");
        String groups = keys.groupTotalKey();
        String members = keys.groupMembersKey("guild-1");

        assertThat(groups.substring(groups.indexOf('{'), groups.indexOf('}') + 1))
                .isEqualTo(members.substring(members.indexOf('{'), members.indexOf('}') + 1));
        assertThat(keys.legacyGroupTotalKey()).isEqualTo("myapp:rank:season-1:groups");
    }
}
