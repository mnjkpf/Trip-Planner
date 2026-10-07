package com.waylo.user.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.security.converter.RsaKeyConverters;

import java.io.IOException;
import java.io.InputStream;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

@Configuration
public class KeyConfig {

    @Bean
    public RSAPrivateKey rsaPrivateKey(
            @Value("${app.jwt.private-key-location}") Resource resource) throws IOException {
        try (InputStream is = resource.getInputStream()) {
            return RsaKeyConverters.pkcs8().convert(is);   // очікує BEGIN PRIVATE KEY
        }
    }

    @Bean
    public RSAPublicKey rsaPublicKey(
            @Value("${app.jwt.public-key-location}") Resource resource) throws IOException {
        try (InputStream is = resource.getInputStream()) {
            return RsaKeyConverters.x509().convert(is);     // очікує BEGIN PUBLIC KEY
        }
    }
}
