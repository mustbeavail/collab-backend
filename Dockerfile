# ── build stage ─────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -B -Dmaven.test.skip=true clean package

# ── run stage ───────────────────────────────────────────────
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/collab-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
# JAVA_OPTS(힙 캡 등)는 compose env로 주입
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar app.jar"]
