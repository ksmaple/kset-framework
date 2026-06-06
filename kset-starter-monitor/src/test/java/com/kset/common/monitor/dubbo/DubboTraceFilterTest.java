package com.kset.common.monitor.dubbo;

import com.kset.common.monitor.facade.MonitorTypes;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DubboTraceFilterTest {

    @Test
    void consumerTransactionUsesOutgoingRpcType() {
        assertThat(DubboTraceFilter.resolveTransactionType("consumer"))
                .isEqualTo(MonitorTypes.RPC_CONSUMER);
    }

    @Test
    void providerTransactionUsesIncomingRpcType() {
        assertThat(DubboTraceFilter.resolveTransactionType("provider"))
                .isEqualTo(MonitorTypes.RPC_PROVIDER);
    }

    @Test
    void unknownSideKeepsLegacyRpcType() {
        assertThat(DubboTraceFilter.resolveTransactionType("unknown"))
                .isEqualTo(MonitorTypes.RPC);
    }
}
