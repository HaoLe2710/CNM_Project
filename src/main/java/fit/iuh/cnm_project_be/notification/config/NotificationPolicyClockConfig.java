package fit.iuh.cnm_project_be.notification.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class NotificationPolicyClockConfig {

    @Bean
    public Clock notificationPolicyClock() {
        return Clock.systemUTC();
    }
}
