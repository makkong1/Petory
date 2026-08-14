package com.linkup.Petory.global.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class NotificationAsyncConfig {

    /**
     * 알림 부가 채널(Redis 캐시/SSE/FCM) 발송 전용 bounded executor.
     *
     * 이전엔 이름 없는 @Async라 기본 SimpleAsyncTaskExecutor를 썼다 — 풀링 없이 호출마다 새
     * 스레드를 만들어 알림이 몰리면 스레드가 무한정 늘어날 수 있었다. petIntentExecutor와 같은
     * 방식(core→queue→max→reject)으로 상한을 둔다.
     *
     * DB 저장(Notification 엔티티)은 이 executor와 무관하게 이미 커밋된 뒤이므로, 여기서 reject돼도
     * 알림 자체는 유실되지 않는다 — 사용자가 목록을 다시 열면 보인다. 유실되는 건 실시간 푸시뿐이라
     * 부가 채널로 취급해 폐기를 허용한다.
     */
    @Bean("notificationExecutor")
    public Executor notificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(6);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("notification-dispatch-");
        executor.setRejectedExecutionHandler(new DiscardWithWarnPolicy());
        executor.initialize();
        return executor;
    }

    /**
     * Java 기본 DiscardPolicy는 로그를 남기지 않는다.
     * 직접 구현해 warn 로그 후 폐기한다.
     * static inner class이므로 outer @Slf4j log 필드 접근 불가 → 별도 Logger 선언.
     */
    static class DiscardWithWarnPolicy implements RejectedExecutionHandler {
        private static final Logger log = LoggerFactory.getLogger(DiscardWithWarnPolicy.class);

        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
            log.warn("[notificationExecutor] 알림 부가 채널 발송 작업 폐기 — 큐 포화. active={} queued={}",
                    executor.getActiveCount(), executor.getQueue().size());
        }
    }
}
