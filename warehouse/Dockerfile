FROM maven:3.8.5-openjdk-11 AS build
COPY src /home/app/src
COPY pom.xml /home/app
RUN mvn -f /home/app/pom.xml clean package

FROM eclipse-temurin:11-jre-jammy
# Download the latest OpenTelemetry Java agent
RUN mkdir -p /app && \
    apt-get update && apt-get install -y wget && \
    wget -O /app/opentelemetry-javaagent.jar \
    https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/latest/download/opentelemetry-javaagent.jar && \
    apt-get clean && rm -rf /var/lib/apt/lists/*

COPY --from=build /home/app/target/warehouse-1.0-SNAPSHOT-jar-with-dependencies.jar /app/warehouse.jar
USER 15000

# Set OpenTelemetry configuration
ENV OTEL_SERVICE_NAME=warehouse
ENV OTEL_EXPORTER_OTLP_ENDPOINT=http://jaeger.tracing:4317
ENV OTEL_EXPORTER_OTLP_PROTOCOL=grpc
ENV OTEL_TRACES_EXPORTER=otlp
ENV OTEL_METRICS_EXPORTER=none
ENV OTEL_LOGS_EXPORTER=none
ENV OTEL_TRACES_SAMPLER=parentbased_traceidratio
ENV OTEL_TRACES_SAMPLER_ARG=1.0

CMD ["java", "-javaagent:/app/opentelemetry-javaagent.jar", "-jar", "/app/warehouse.jar"]