package com.kset.common.monitor.aop;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class ScheduledMonitorAspectTest {

    @Test
    void describeCronSchedule() throws Exception {
        Method method = SampleTasks.class.getDeclaredMethod("cronTask");

        assertThat(ScheduledMonitorAspect.describeSchedules(method))
                .isEqualTo("cron=0/5 * * * * *,zone=Asia/Shanghai");
    }

    @Test
    void describeFixedRateSchedule() throws Exception {
        Method method = SampleTasks.class.getDeclaredMethod("fixedRateTask");

        assertThat(ScheduledMonitorAspect.describeSchedules(method))
                .isEqualTo("fixedRate=1000,initialDelay=200");
    }

    static class SampleTasks {

        @Scheduled(cron = "0/5 * * * * *", zone = "Asia/Shanghai")
        void cronTask() {
        }

        @Scheduled(fixedRate = 1000, initialDelay = 200)
        void fixedRateTask() {
        }
    }
}
