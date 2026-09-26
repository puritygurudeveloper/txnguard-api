# Two stages: the JDK and the Maven cache stay out of the shipped image.
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /build

# Dependencies resolve in their own layer so a source-only change does not
# re-download the world on every build.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -B -q dependency:go-offline

COPY src/ src/
RUN ./mvnw -B -q -DskipTests package

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Never root. A payments service is an attractive target and the JVM has no
# reason to own anything in the filesystem.
RUN useradd --system --uid 10001 --create-home txnguard
USER txnguard

COPY --from=build --chown=txnguard:txnguard /build/target/txnguard-*.jar app.jar

EXPOSE 8080

# Let the JVM see the container's cgroup limits rather than the host's.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseContainerSupport -XX:+ExitOnOutOfMemoryError"

HEALTHCHECK --interval=15s --timeout=3s --start-period=40s --retries=3 \
  CMD curl -sf http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
