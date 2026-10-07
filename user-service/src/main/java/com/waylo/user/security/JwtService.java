package com.waylo.user.security;

import com.waylo.user.domain.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class JwtService {

    private final JwtEncoder jwtEncoder;
    private final String issuer;
    private final String keyId;
    private final Duration accessTtl;

    public JwtService(JwtEncoder jwtEncoder,
                      @Value("${app.jwt.issuer}") String issuer,
                      @Value("${app.jwt.key-id}") String keyId,
                      @Value("${app.jwt.access-ttl}") Duration accessTtl) {
        this.jwtEncoder = jwtEncoder;
        this.issuer = issuer;
        this.keyId = keyId;
        this.accessTtl = accessTtl;
    }

    public String issueAccessToken(User user) {
        Instant now = Instant.now();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .issuedAt(now)
                .expiresAt(now.plus(accessTtl))
                .subject(user.getId().toString())
                .id(UUID.randomUUID().toString())           // jti
                .claim("uid", user.getId().toString())      // → gateway → X-User-Id
                .claim("role", user.getRole().name())       // → gateway → X-User-Role
                .claim("email", user.getEmail())
                .build();

        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256)
                .keyId(keyId)
                .build();

        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public long accessTtlSeconds() {
        return accessTtl.toSeconds();
    }
}
