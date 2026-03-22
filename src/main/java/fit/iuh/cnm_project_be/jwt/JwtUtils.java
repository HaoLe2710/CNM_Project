package fit.iuh.cnm_project_be.jwt;

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
import java.time.temporal.ChronoUnit;
import java.util.Map;

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

//    tao token
    public String generateToken(String userName) {
        try {
            Instant now = Instant.now();
            JwsHeader jwsHeader = JwsHeader.with(SignatureAlgorithm.RS256).build();

            JwtClaimsSet jwtClaimsSet = JwtClaimsSet.builder()
                    .subject(userName)
                    .issuer(issuer)
                    .issuedAt(now)
                    .expiresAt(now.plus(1, ChronoUnit.HOURS))
                    .build();

            return jwtEncoder
                    .encode(JwtEncoderParameters.from(jwsHeader, jwtClaimsSet))
                    .getTokenValue();
        } catch (Exception e) {
            log.error("JwtUtils: Error when sign Token: {}", e.getMessage());
            throw new RuntimeException("Could not generate token", e);
        }
    }

//    giai ma token
    public Jwt decodeToken(String token) {
        return jwtDecoder.decode(token);
    }
}