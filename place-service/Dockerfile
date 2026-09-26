# syntax=docker/dockerfile:1
# ---- build ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
# Спершу тільки pom — щоб шар із залежностями кешувався окремо від коду.
COPY pom.xml .
COPY src ./src
# BuildKit cache mount: локальний .m2 переживає перезбірки й спільний для всіх сервісів,
# тож залежності тягнуться фактично один раз. Тести не гоняємо в образі (їх ганяє mvnw verify).
RUN --mount=type=cache,target=/root/.m2 mvn -B -Dmaven.test.skip=true clean package

# ---- run ----
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /build/target/*.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
