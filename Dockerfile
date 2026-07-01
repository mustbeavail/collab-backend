# ── build stage ─────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -B -Dmaven.test.skip=true clean package

# ── run stage ───────────────────────────────────────────────
FROM eclipse-temurin:21-jre
WORKDIR /app
# 음성 회의록: webm→ogg 변환용 ffmpeg (PATH의 'ffmpeg' 로 호출됨)
# 운영 EC2 보안그룹 아웃바운드가 443만 허용 → apt 미러(기본 http:80)를 https로 전환 후 설치
RUN sed -i 's|http://|https://|g' /etc/apt/sources.list.d/ubuntu.sources \
    && apt-get update \
    && apt-get install -y --no-install-recommends ffmpeg \
    && rm -rf /var/lib/apt/lists/*
COPY --from=build /app/target/collab-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
# JAVA_OPTS(힙 캡 등)는 compose env로 주입
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar app.jar"]
