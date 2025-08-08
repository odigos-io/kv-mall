# OpenTelemetry Instrumentation for kv-mall

This document describes the OpenTelemetry instrumentation that has been added to the kv-mall microservices application.

## Overview

The kv-mall application has been instrumented with OpenTelemetry auto-instrumentation using the Java agent, plus custom manual spans for enhanced observability. All telemetry data (traces, metrics, and logs) is configured to be sent to Jaeger at `jaeger.tracing:4317`.

## Changes Made

### 1. Dockerfile Modifications

All Java service Dockerfiles have been updated to:

- **Download the latest OpenTelemetry Java agent** from GitHub releases
- **Configure environment variables** for OpenTelemetry
- **Add the `-javaagent` parameter** to the Java command line

#### Services Updated:
- `warehouse/Dockerfile`
- `pricing/Dockerfile` 
- `inventory/Dockerfile`
- `frontend/Dockerfile`

#### Key Configuration:
```bash
ENV OTEL_SERVICE_NAME=<service-name>
ENV OTEL_EXPORTER_OTLP_ENDPOINT=http://jaeger.tracing:4317
ENV OTEL_EXPORTER_OTLP_PROTOCOL=grpc
ENV OTEL_TRACES_EXPORTER=otlp
ENV OTEL_METRICS_EXPORTER=otlp
ENV OTEL_LOGS_EXPORTER=otlp
ENV OTEL_TRACES_SAMPLER=parentbased_traceidratio
ENV OTEL_TRACES_SAMPLER_ARG=1.0

CMD ["java", "-javaagent:/app/opentelemetry-javaagent.jar", "-jar", "/app/<service>.jar"]
```

### 2. Maven Dependencies

All Java services have been updated with the OpenTelemetry API dependency for manual instrumentation:

```xml
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-api</artifactId>
    <version>1.52.0</version>
</dependency>
```

### 3. Manual Instrumentation Added

#### Pricing Service (`PricingController.java`)
- **Custom spans** for price calculations and database lookups
- **Attributes** for product IDs, prices, and processing steps
- **Events** for RxJava processing stages

**Key spans:**
- `pricing.get_price` - Main price retrieval operation
- `db.price.lookup` - Database price lookup simulation

#### Frontend Service (`ProductController.java`)
- **Comprehensive spans** for product listing and purchasing
- **Child spans** for pricing operations and auxiliary services
- **Parallel processing tracking** with custom events

**Key spans:**
- `frontend.get_products` - Product listing with child spans for pricing and auxiliary services
- `frontend.buy_product` - Purchase operation with validation and service calls
- `frontend.price_products` - Batch pricing operations
- `frontend.auxiliary_services` - Coupon and advertisement fetching

#### Inventory Service (`InventoryController.java`)
- **Business operation tracking** for inventory management
- **Kafka message publishing** with detailed messaging spans
- **External service calls** with lock request tracking

**Key spans:**
- `inventory.get_all` - Inventory listing
- `inventory.buy_product` - Purchase processing with special watch product handling
- `inventory.publish_order` - Kafka message publishing
- `inventory.trigger_lock` - External service call for product locking

#### Warehouse Service (`WarehouseConsumer.java`)
- **Kafka message consumption** with comprehensive tracking
- **Individual message processing** spans
- **Header processing** with attribute extraction

**Key spans:**
- `warehouse.consume_orders` - Main consumption loop
- `warehouse.poll_messages` - Each polling operation
- `warehouse.process_order` - Individual message processing

## Telemetry Features

### Automatic Instrumentation
The OpenTelemetry Java agent automatically instruments:
- **HTTP requests/responses** (Spring Boot controllers)
- **JDBC database calls** (if applicable)
- **Kafka producer/consumer operations**
- **External HTTP client calls**
- **JVM metrics** (memory, CPU, garbage collection)

### Manual Instrumentation
Custom spans provide business-specific insights:
- **Business operation tracking** (purchases, inventory lookups)
- **Service interaction patterns** (pricing, currency conversion)
- **Message processing workflows** (Kafka events)
- **Error handling and exception tracking**

### Span Attributes
Rich contextual information including:
- `service.name`, `product.id`, `operation.type`
- `http.method`, `http.endpoint`
- `messaging.system`, `messaging.destination`
- `price.calculated`, `inventory.size`, `conversion.rate`
- Custom business attributes for enhanced filtering

### Events
Detailed operation progress tracking:
- `starting.inventory.fetch`, `pricing.product`
- `kafka.message.sent`, `order.processing.completed`
- `purchase.validation`, `parallel.pricing.completed`

## Deployment Instructions

### Prerequisites
Ensure Jaeger is running and accessible at `jaeger.tracing:4317` with OTLP gRPC receiver enabled.

### Building and Running
1. **Build the Docker images** (OpenTelemetry agent will be downloaded during build):
```bash
docker build -t kv-mall-frontend ./frontend
docker build -t kv-mall-pricing ./pricing
docker build -t kv-mall-inventory ./inventory
docker build -t kv-mall-warehouse ./warehouse
```

2. **Run the services** - The containers will automatically:
   - Download the latest OpenTelemetry Java agent
   - Configure telemetry export to `jaeger.tracing:4317`
   - Start with both auto and manual instrumentation active

### Environment Variables
You can override the default OpenTelemetry configuration:

```bash
# Change the Jaeger endpoint
OTEL_EXPORTER_OTLP_ENDPOINT=http://your-jaeger:4317

# Adjust sampling rate (0.0 to 1.0)
OTEL_TRACES_SAMPLER_ARG=0.5

# Change service name
OTEL_SERVICE_NAME=custom-service-name

# Enable debug logging
OTEL_JAVAAGENT_DEBUG=true
```

## Observability Features

### Distributed Tracing
- **End-to-end request tracking** across all services
- **Parent-child span relationships** showing service interactions
- **Asynchronous operation tracking** (RxJava, CompletableFuture)
- **Kafka message correlation** between producer and consumer

### Business Metrics
- **Purchase transaction tracking** with pricing and inventory
- **Product popularity** via pricing request frequency
- **Service performance** via span duration analysis
- **Error rate monitoring** via span status tracking

### Integration Benefits
- **Jaeger UI** for trace visualization and analysis
- **Service dependency mapping** automatic discovery
- **Performance bottleneck identification** via span timing
- **Error propagation tracking** across service boundaries

## Troubleshooting

### Common Issues
1. **Agent download failures**: Check internet connectivity during Docker build
2. **Connection refused to Jaeger**: Verify Jaeger is running on the correct host/port
3. **Missing traces**: Check OTEL_TRACES_SAMPLER_ARG is not set too low
4. **High overhead**: Reduce sampling rate or disable specific instrumentations

### Debug Mode
Enable debug logging:
```bash
OTEL_JAVAAGENT_DEBUG=true
OTEL_LOG_LEVEL=debug
```

### Health Checks
The instrumentation adds minimal overhead but provides rich observability. Monitor:
- **Application startup time** (should increase slightly due to agent initialization)
- **Memory usage** (small increase for telemetry overhead)
- **Network traffic** to Jaeger (traces, metrics, logs export)

## Next Steps

Consider adding:
- **Custom metrics** for business KPIs (cart value, conversion rates)
- **Log correlation** with trace IDs
- **Alert rules** based on trace data in Jaeger
- **Service level objectives** using trace-derived metrics
- **Chaos engineering** with fault injection to test trace visibility during failures