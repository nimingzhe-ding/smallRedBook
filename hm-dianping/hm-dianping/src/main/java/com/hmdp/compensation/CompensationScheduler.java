package com.hmdp.compensation;

import com.hmdp.config.CompensationProperties;
import com.hmdp.service.CompensationEventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CompensationScheduler {

    private final CompensationProperties properties;
    private final CompensationEventService compensationEventService;

    @Scheduled(fixedDelayString = "${hmdp.compensation.fixed-delay-millis:5000}")
    public void replayDueEvents() {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            int count = compensationEventService.replayDue(properties.getBatchSize());
            if (count > 0) {
                log.info("Compensation replay completed, count={}", count);
            }
        } catch (Exception e) {
            log.warn("Compensation scheduler tick failed", e);
        }
    }
}
