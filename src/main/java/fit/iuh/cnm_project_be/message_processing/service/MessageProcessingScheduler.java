package fit.iuh.cnm_project_be.message_processing.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class MessageProcessingScheduler {

    private final MessageProcessingService messageProcessingService;

    @Scheduled(fixedDelayString = "${app.message-processing.scheduler-fixed-delay-ms:10000}")
    public void processPendingJobs() {
        try {
            int processed = messageProcessingService.processPendingJobs(Instant.now());
            if (processed > 0) {
                log.info("[MessageProcessingScheduler] processedJobs={}", processed);
            }
        } catch (Exception ex) {
            log.warn("[MessageProcessingScheduler] failed: {}", ex.getMessage());
        }
    }
}
