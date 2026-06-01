package fit.iuh.cnm_project_be.config;


import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@Configuration
public class FirebaseConfig {

    private static final String FIREBASE_CLASSPATH_RESOURCE = "/firebase-service-account.json";

    @Value("${firebase.service-account.path:${FIREBASE_SERVICE_ACCOUNT_PATH:${GOOGLE_APPLICATION_CREDENTIALS:}}}")
    private String firebaseServiceAccountPath;

    @PostConstruct
    public void init() throws IOException {
        List<FirebaseApp> firebaseApps = FirebaseApp.getApps();
        if (!firebaseApps.isEmpty()) {
            return;
        }

        try (InputStream serviceAccount = openServiceAccount()) {
            FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                .build();

            FirebaseApp.initializeApp(options);
        }
    }

    @Bean
    public FirebaseMessaging firebaseMessaging() {
        return FirebaseMessaging.getInstance();
    }

    private InputStream openServiceAccount() throws IOException {
        if (firebaseServiceAccountPath != null && !firebaseServiceAccountPath.isBlank()) {
            return new FileInputStream(firebaseServiceAccountPath.trim());
        }

        InputStream serviceAccount = getClass().getResourceAsStream(FIREBASE_CLASSPATH_RESOURCE);
        if (serviceAccount == null) {
            throw new IOException(
                "Missing Firebase service account. Set FIREBASE_SERVICE_ACCOUNT_PATH or GOOGLE_APPLICATION_CREDENTIALS");
        }
        return serviceAccount;
    }
}
