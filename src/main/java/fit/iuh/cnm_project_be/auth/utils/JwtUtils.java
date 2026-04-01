package fit.iuh.cnm_project_be.auth.utils;

import fit.iuh.cnm_project_be.auth.entity.Account;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@Slf4j
public class JwtUtils {
    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;
    private final String issuer;

    public JwtUtils(
            JwtEncoder jwtEncoder,
            JwtDecoder jwtDecoder,
            @Value("${app.jwt.issuer:http://cnm-project}") String issuer
    ) {
        this.jwtEncoder = jwtEncoder;
        this.jwtDecoder = jwtDecoder;
        this.issuer = issuer;
    }

//    Giai ma va lay ra thong tin
    public Map<String, Object> getClaims(String token) {
        try {
            return jwtDecoder.decode(token).getClaims();
        } catch (Exception e) {
            log.warn("JwtUtils: Could not parse token: {}", e.getMessage());
            return null;
        }
    }

//    lay username từ token
    public String getUsernameFromToken(String token) {
        Map<String, Object> claims = getClaims(token);
        return claims != null ? (String) claims.get("sub") : null;
    }

    //    lay deviceId từ token
    public String getDeviceIdFromToken(String token) {
        Map<String, Object> claims = getClaims(token);
        return claims != null ? (String) claims.get("deviceId") : null;
    }

    //    lay platform từ token
    public String getPlatformFromToken(String token) {
        Map<String, Object> claims = getClaims(token);
        return claims != null ? (String) claims.get("platform") : null;
    }

//    lay thoi gian het han
    public LocalDateTime getExpiresIn(String token) {
        try {
            Instant expiresAt = jwtDecoder.decode(token).getExpiresAt();
            return expiresAt != null ?
                    LocalDateTime.ofInstant(expiresAt, java.time.ZoneId.systemDefault()) : null;
        } catch (Exception e) {
            return null;
        }
    }

//  tao refresh token
    public String generateRefreshToken() {
        return UUID.randomUUID().toString();
    }

//    tao  access token
    public String generateToken(Account account) {
        return generateToken(account, null, null);
    }

    public String generateToken(Account account, String deviceId, String platform) {
        try {
            Instant now = Instant.now();

            String scope = account.getRoles().stream()
                    .map(Enum::name)
                    .collect(Collectors.joining(" "));

            JwsHeader jwsHeader = JwsHeader.with(SignatureAlgorithm.RS256).build();

            JwtClaimsSet.Builder claimsBuilder = JwtClaimsSet.builder()
                    .issuer(issuer)
                    .issuedAt(now)
                    .expiresAt(now.plus(1, ChronoUnit.HOURS))
                    .subject(account.getUsername())
                    .claim("userId", account.getUserId().toString())
                    .claim("scope", scope);

            if (deviceId != null) {
                claimsBuilder.claim("deviceId", deviceId);
            }
            if (platform != null) {
                claimsBuilder.claim("platform", platform);
            }

            JwtClaimsSet jwtClaimsSet = claimsBuilder.build();

            return jwtEncoder
                    .encode(JwtEncoderParameters.from(jwsHeader, jwtClaimsSet))
                    .getTokenValue();
        } catch (Exception e) {
            log.error("JwtUtils: Error when sign Token for user: {}: {}", account.getUsername(), e.getMessage());
            throw new RuntimeException("Could not generate token", e);
        }
    }

//    giai ma token
    public Jwt decodeToken(String token) {
        return jwtDecoder.decode(token);
    }
}