package fit.iuh.cnm_project_be.config;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import fit.iuh.cnm_project_be.auth.entity.Account;
import fit.iuh.cnm_project_be.auth.enums.Role;
import fit.iuh.cnm_project_be.auth.repository.AccountRepository;
import fit.iuh.cnm_project_be.user.entity.UserProfile;
import fit.iuh.cnm_project_be.user.entity.UserDevice;
import fit.iuh.cnm_project_be.user.enums.Platform;
import fit.iuh.cnm_project_be.user.repository.UserProfileRepository;
import fit.iuh.cnm_project_be.user.repository.UserDeviceRepository;
import jakarta.servlet.http.Cookie;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.util.StreamUtils;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    private static final String PRIVATE_KEY_PATH = "certs/private_key.pem";
    private static final String PUBLIC_KEY_PATH = "certs/public_key.pem";
    private static final String DEFAULT_ALLOWED_ORIGINS = "http://localhost:5173,http://localhost:3000";

    private static final String[] PUBLIC_END_POINT = {
            "/api/v1/test/**",
            "/api/v1/auth/**",
            "/actuator/health",
            "/actuator/health/**",
            "/error",
            "/ws/**",
            "/auth/ws/**",
    };

    @Value("${app.cors.allowed-origins:${APP_CORS_ALLOWED_ORIGINS:" + DEFAULT_ALLOWED_ORIGINS + "}}")
    private String corsAllowedOriginsRaw;

    @Value("${app.jwt.private-key-path:${APP_JWT_PRIVATE_KEY_PATH:}}")
    private String jwtPrivateKeyPath;

    @Value("${app.jwt.public-key-path:${APP_JWT_PUBLIC_KEY_PATH:}}")
    private String jwtPublicKeyPath;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_END_POINT).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        // 1. Cấu hình để Spring tự tìm Token trong Cookie thay vì Header
                        .bearerTokenResolver(request -> {
                            Cookie[] cookies = request.getCookies();
                            if (cookies != null) {
                                for (Cookie cookie : cookies) {
                                    if ("accessToken".equals(cookie.getName())) {
                                        return cookie.getValue();
                                    }
                                }
                            }
                            return null;
                        })
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));

        return http.build();
    }

    // Hàm này giúp Spring hiểu "scope" trong JWT chính là các Authorities (Roles)
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        // Vì trong hàm generateToken bạn lưu dưới dạng "scope", mặc định Spring sẽ tìm
        // claim này
        // Chúng ta thêm tiền tố ROLE_ để dùng được hasRole('ADMIN')
        authoritiesConverter.setAuthorityPrefix("ROLE_");
        authoritiesConverter.setAuthoritiesClaimName("scope");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        return converter;
    }

    // Tong hop 2 key vao context de quan ly va lay ra moi khi can, khong can doc
    // file pem lai
    @Bean
    public RSAKey rsaJwk() throws IOException, JOSEException {
        String privatePem = readPem(jwtPrivateKeyPath, PRIVATE_KEY_PATH);
        String publicPem = readPem(jwtPublicKeyPath, PUBLIC_KEY_PATH);

        RSAKey privateRsa = (RSAKey) JWK.parseFromPEMEncodedObjects(privatePem);
        RSAKey publicRsa = (RSAKey) JWK.parseFromPEMEncodedObjects(publicPem);

        return new RSAKey.Builder(publicRsa.toRSAPublicKey())
                .privateKey(privateRsa.toRSAPrivateKey())
                .build();
    }

    // lay private key tu context
    @Bean
    public RSAPrivateKey rsaPrivateKey(RSAKey rsaJwk) throws JOSEException {
        return rsaJwk.toRSAPrivateKey();
    }

    // lay public key tu context
    @Bean
    public RSAPublicKey rsaPublicKey(RSAKey rsaJwk) throws JOSEException {
        return rsaJwk.toRSAPublicKey();
    }

    // giai ma token
    @Bean
    public JwtDecoder jwtDecoder(RSAPublicKey rsaPublicKey) {
        return NimbusJwtDecoder
                .withPublicKey(rsaPublicKey)
                .build();
    }

    // bao mat, chuyen thong tin user thanh token
    @Bean
    JwtEncoder jwtEncoder(RSAPublicKey rsaPublicKey, RSAPrivateKey rsaPrivateKey) {
        RSAKey rsa = new RSAKey.Builder(rsaPublicKey)
                .privateKey(rsaPrivateKey)
                .build();

        JWKSource<SecurityContext> jwtks = new ImmutableJWKSet<>(new JWKSet(rsa));
        return new NimbusJwtEncoder(jwtks);
    }

    // ma hoa password cua user, tranh lo thong tin
    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // doc file pem tu mounted secret path neu co, fallback classpath cho local/dev
    private String readPem(String filePath, String classpathFile) throws IOException {
        if (filePath != null && !filePath.isBlank()) {
            return Files.readString(Path.of(filePath.trim()), StandardCharsets.UTF_8);
        }

        try (InputStream inputStream = new ClassPathResource(classpathFile).getInputStream()) {
            return StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
        }
    }

    @Bean
    public UserDetailsService userDetailsService(AccountRepository accountRepository) {
        return username -> {
            Account account = accountRepository.findByUsername(username)
                    .orElseThrow(() -> new UsernameNotFoundException("User not found"));

            return User.builder()
                    .username(account.getUsername())
                    .password(account.getPassword())
                    .authorities(account.getRoles().stream()
                            .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                            .toList())
                    .build();
        };
    }

    @Bean
    CommandLineRunner initDatabase(AccountRepository accountRepository, PasswordEncoder passwordEncoder,
            UserProfileRepository userProfileRepository, UserDeviceRepository userDeviceRepository) {
        UUID userId = UUID.randomUUID();
        return args -> {
            if (accountRepository.findByUsername("admin").isEmpty()) {
                Account adminAccount = Account.builder()
                        .userId(userId)
                        .username("admin")
                        .password(passwordEncoder.encode("@Admin123"))
                        .roles(List.of(Role.ADMIN, Role.USER))
                        .build();

                accountRepository.save(adminAccount);

                UserProfile adminProfile = UserProfile.builder()
                        .userId(userId)
                        .username("admin")
                        .displayName("Admin")
                        .build();

                userProfileRepository.save(adminProfile);

                // Tạo device mặc định cho admin
                UserDevice adminDevice = new UserDevice();
                adminDevice.setUserId(userId);
                adminDevice.setDeviceId("admin-default-device");
                adminDevice.setPlatform(Platform.WEB);
                adminDevice.setDeviceName("Admin Web");

                userDeviceRepository.save(adminDevice);

                System.out.println(">>> SecurityConfig: Created default admin account with password: @Admin123");
            }
        };
    }

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        String[] allowedOrigins = parseAllowedOrigins(corsAllowedOriginsRaw);

        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                // Thêm CORS cho API
                registry.addMapping("/api/**")
                        .allowedOrigins(allowedOrigins)
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
                        .allowedHeaders("*")
                        .allowCredentials(true)
                        .maxAge(3600);

                // Thêm CORS cho WebSocket endpoints (cần cho SockJS handshake)
                registry.addMapping("/ws/**")
                        .allowedOrigins(allowedOrigins)
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
                        .allowedHeaders("*")
                        .allowCredentials(true)
                        .maxAge(3600);

                registry.addMapping("/auth/ws/**")
                        .allowedOrigins(allowedOrigins)
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
                        .allowedHeaders("*")
                        .allowCredentials(true)
                        .maxAge(3600);
            }
        };
    }

    private String[] parseAllowedOrigins(String rawOrigins) {
        String source = rawOrigins;
        if (source == null || source.isBlank()) {
            source = DEFAULT_ALLOWED_ORIGINS;
        }

        return Arrays.stream(source.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toArray(String[]::new);
    }
}
