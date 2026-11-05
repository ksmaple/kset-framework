package com.kset.dubbo.reference;

import org.apache.dubbo.common.URL;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DubboRemoteServiceInspectorTest {

    @Test
    void ignoresProviderExportedByThisProcess() {
        URL own = URL.valueOf("dubbo://10.0.0.1:20880/com.kset.GreetingService?group=orders&version=1.0");

        assertThat(DubboRemoteServiceInspector.hasOtherProcessProvider(
                List.of(own), List.of(own), own.getServiceKey())).isFalse();
    }

    @Test
    void detectsAnotherProcessWithSameServiceKey() {
        URL own = URL.valueOf("dubbo://10.0.0.1:20880/com.kset.GreetingService?group=orders&version=1.0");
        URL other = URL.valueOf("dubbo://10.0.0.2:20880/com.kset.GreetingService?group=orders&version=1.0");

        assertThat(DubboRemoteServiceInspector.hasOtherProcessProvider(
                List.of(own, other), List.of(own), own.getServiceKey())).isTrue();
    }

    @Test
    void ignoresOtherGroupsWhenCheckingThisService() {
        URL own = URL.valueOf("dubbo://10.0.0.1:20880/com.kset.GreetingService?group=orders&version=1.0");
        URL other = URL.valueOf("dubbo://10.0.0.1:20880/com.kset.GreetingService?group=other&version=1.0");

        assertThat(DubboRemoteServiceInspector.hasOtherProcessProvider(
                List.of(other), List.of(own), own.getServiceKey())).isFalse();
    }

    @Test
    void ignoresOtherVersionsWhenCheckingThisService() {
        URL own = URL.valueOf("dubbo://10.0.0.1:20880/com.kset.GreetingService?group=orders&version=1.0");
        URL other = URL.valueOf("dubbo://10.0.0.2:20880/com.kset.GreetingService?group=orders&version=2.0");

        assertThat(DubboRemoteServiceInspector.hasOtherProcessProvider(
                List.of(other), List.of(own), own.getServiceKey())).isFalse();
    }
}
