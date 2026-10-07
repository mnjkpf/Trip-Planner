package com.waylo.user.web;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKMatcher;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class JwksController {

    private final JWKSource<SecurityContext> jwkSource;

    public JwksController(JWKSource<SecurityContext> jwkSource) {
        this.jwkSource = jwkSource;
    }

    // Точно цей шлях чекає gateway (jwk-set-uri).
    // Віддаємо ТІЛЬКИ публічну частину — toPublicJWKSet().
    @GetMapping("/.well-known/jwks.json")
    public Map<String, Object> jwks() throws Exception {
        List<JWK> keys = jwkSource.get(new JWKSelector(new JWKMatcher.Builder().build()), null);
        return new JWKSet(keys).toPublicJWKSet().toJSONObject();
    }
}
