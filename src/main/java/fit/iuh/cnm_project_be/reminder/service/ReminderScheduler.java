package fit.iuh.cnm_project_be.reminder.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReminderScheduler {

    private final ConversationReminderService reminderService;

    @Scheduled(fixedDelayString = "${app.reminder.scheduler-fixed-delay-ms:60000}")
    public void processDueReminders() {
        try {
            int processed = reminderService.processDueReminders(Instant.now());
            if (processed > 0) {
                log.info("[ReminderScheduler] processedDueReminders={}", processed);
            }
        } catch (Exception ex) {
            log.warn("[ReminderScheduler] failed: {}", ex.getMessage());
        }
    }
}
