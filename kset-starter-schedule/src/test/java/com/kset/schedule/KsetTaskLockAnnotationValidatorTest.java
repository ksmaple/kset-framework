package com.kset.schedule;

import com.kset.schedule.config.KsetTaskLockAnnotationValidator;
import com.kset.schedule.annotation.KsetTaskLock;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.stereotype.Component;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KsetTaskLockAnnotationValidatorTest {

    @Test
    void passesWhenDurationsValid() {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(ValidTask.class)) {
            KsetTaskLockAnnotationValidator validator = new KsetTaskLockAnnotationValidator(context);
            assertThatCode(validator::afterSingletonsInstantiated).doesNotThrowAnyException();
        }
    }

    @Test
    void failsFastWhenDurationInvalid() {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(InvalidTask.class)) {
            KsetTaskLockAnnotationValidator validator = new KsetTaskLockAnnotationValidator(context);
            assertThatThrownBy(validator::afterSingletonsInstantiated)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("atMostFor")
                    .hasMessageContaining("InvalidTask");
        }
    }

    @Component
    static class ValidTask {
        @KsetTaskLock(atMostFor = "10m", atLeastFor = "30s")
        public void run() {
        }
    }

    @Component
    static class InvalidTask {
        @KsetTaskLock(atMostFor = "十分钟")
        public void run() {
        }
    }
}
