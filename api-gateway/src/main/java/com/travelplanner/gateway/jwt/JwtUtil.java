package com.travelplanner.gateway.jwt;

import javax.crypto.SecretKey;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component // Marks this class as a Spring-managed component.
public class JwtUtil { // Defines a utility class for JWT-related operations.

    @Value("${app.jwt.secret}") // Injects the JWT secret from application configuration.
    private String secret; // Stores the configured secret used to verify JWT signatures.

    private SecretKey getKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secret);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public Claims validateToken(String token){ // Validates a JWT token and returns its claims payload.
        return Jwts.parser() // Starts building a JWT parser instance.
        .verifyWith(getKey()) // Configures the parser to verify the token with the generated key.
        .build() // Finalizes the parser configuration.
        .parseSignedClaims(token) // Parses the signed JWT and validates its signature.
        .getPayload(); // Extracts and returns the claims payload from the parsed token.
    } // Ends the token validation method.

} // Ends the JwtUtil class.
