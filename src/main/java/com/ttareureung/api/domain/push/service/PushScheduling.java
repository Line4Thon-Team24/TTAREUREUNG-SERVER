package com.ttareureung.api.domain.push.service;

import com.ttareureung.api.domain.senior.service.SeniorTime;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Clock;
import java.time.LocalDateTime;

// VAPID 키가 있을 때만 스케줄러 동작. 키 없는 로컬·테스트에서는 회차를 임의로 만들지 않음
@Configuration
@EnableScheduling
@ConditionalOnExpression("'${push.vapid-public-key:}' != '' and '${push.vapid-private-key:}' != ''")
public class PushScheduling {

    private final DosePushJob job;
    private final Clock clock;
    private LocalDateTime previousRun;

    public PushScheduling(DosePushJob job, Clock clock) {
        this.job = job;
        this.clock = clock;
        this.previousRun = now().minusMinutes(1);
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 10_000)
    public void run() {
        LocalDateTime now = now();
        job.runOnce(previousRun, now);
        previousRun = now;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock.withZone(SeniorTime.ZONE));
    }
}
