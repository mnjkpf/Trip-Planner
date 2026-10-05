package com.waylo.offer.config;

/* Кешем керує Spring Boot авто-конфігурація Redis-кешу: TTL і префікси —
 * у application.yml під {@code spring.cache.*}. Власний менеджер нам не
 * потрібен (кешуємо сирий JSON-рядок, не об'єкти — стандартного
 * серіалізатора вистачає). */
