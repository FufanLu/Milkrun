# Runtime image for the sync service. The fat jar is built by Gradle first:
#   ./gradlew :server:app:buildFatJar && docker build -t milkrun-server server/app
FROM amazoncorretto:21-alpine
RUN addgroup -S milkrun && adduser -S milkrun -G milkrun
WORKDIR /app
COPY build/libs/milkrun-server.jar /app/milkrun-server.jar
USER milkrun
EXPOSE 8080
# Container-aware heap; the JVM reads the cgroup memory limit.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
HEALTHCHECK --interval=10s --timeout=2s --retries=3 CMD wget -qO- http://localhost:8080/healthz || exit 1
ENTRYPOINT ["java", "-jar", "/app/milkrun-server.jar"]
