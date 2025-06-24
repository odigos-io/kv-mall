# Logging Migration: Console.log to Pino with OpenTelemetry Correlation

## Overview

This document describes the migration of NodeJS applications from `console.log` to structured logging using **Pino** with **OpenTelemetry trace correlation**. The goal is to enable correlation between automatic instrumentation traces and application logs for better observability.

## Changes Made

### 1. Dependencies Added

#### Coupon Service (`coupon/package.json`)
- `pino`: ^8.15.0 - Fast JSON logger for Node.js
- `pino-http`: ^8.5.0 - HTTP request logging middleware
- `@opentelemetry/api`: ^1.6.0 - OpenTelemetry API for trace correlation

#### Mail Service (`mail/package.json`)
- `pino`: ^8.15.0 - Fast JSON logger for Node.js
- `@opentelemetry/api`: ^1.6.0 - OpenTelemetry API for trace correlation

#### Webapp Frontend (`webapp/`)
- Custom browser-compatible logger utility created (`utils/logger.js`)
- No additional dependencies required (uses existing Next.js setup)

### 2. Logger Utilities Created

#### Coupon Service (`coupon/logger.js`)
- **Trace Correlation**: Automatically adds `traceId`, `spanId`, and `traceFlags` to all log entries
- **Structured Logging**: JSON format with proper serializers for requests, responses, and errors
- **Helper Functions**:
  - `logHttpRequest()`: Log HTTP requests with timing and status codes
  - `logExternalCall()`: Log calls to external services (membership service)
  - `logKafkaOperation()`: Log Kafka producer operations

#### Mail Service (`mail/logger.ts`)
- **Trace Correlation**: Automatically adds OpenTelemetry trace context to all log entries
- **TypeScript Support**: Proper type definitions
- **Helper Functions**:
  - `logKafkaOperation()`: Log Kafka consumer operations
  - `logMessageProcessing()`: Log message processing with timing

#### Webapp Frontend (`webapp/utils/logger.js`)
- **Browser-Compatible**: Works in client-side JavaScript environment
- **Environment-Aware**: Different logging behavior for development vs production
- **Structured Data**: Includes timestamp, user agent, and URL context
- **Helper Functions**:
  - `logApiCall()`: Log API calls with timing and success/failure status
  - Standard log levels: trace, debug, info, warn, error, fatal

### 3. Application Updates

#### Coupon Service (`coupon/app.js`)
**Before:**
```javascript
console.log('Coupon request received!');
console.log('Got response from membership service!', response.data);
```

**After:**
```javascript
logger.info({ 
  endpoint: '/coupons',
  method: 'GET',
  userAgent: req.headers['user-agent'],
  ip: req.ip
}, 'Coupon request received');

logExternalCall('membership-service', 'GET', '/isMember', response.status, externalCallTime, true);
```

**New Features:**
- HTTP request/response middleware with `pino-http`
- Request timing and performance metrics
- Structured error handling with proper status codes
- Kafka operation logging with timing
- Graceful shutdown logging

#### Mail Service (`mail/index.ts`)
**Before:**
```typescript
console.log('Connected to Kafka!');
console.log("mock sending email to user ");
```

**After:**
```typescript
logKafkaOperation('connect', 'consumer', true, null, { retryCount });

logger.info({
  email: {
    action: 'sending',
    recipient: 'user',
    template: 'coupon-applied'
  },
  messageData: messageValue ? JSON.parse(messageValue) : null
}, 'Sending email notification to user');
```

**New Features:**
- Kafka message processing with detailed metadata
- Message processing timing
- Structured error handling for message processing
- Service startup and shutdown logging

#### Webapp Frontend (`webapp/components/ProductCard.js`)
**Before:**
```javascript
.catch(err => console.log(err));
```

**After:**
```javascript
logger.info('Initiating product purchase', {
  productId: product.id,
  productName: product.name,
  productPrice: product.price
});

.catch(err => {
  const responseTime = Date.now() - startTime;
  logApiCall('POST', url, 0, responseTime, false, {
    name: err.name,
    message: err.message
  });
  
  logger.error('Product purchase failed', {
    productId: product.id,
    error: { name: err.name, message: err.message },
    responseTime
  });
});
```

**New Features:**
- API call timing and success/failure tracking
- Structured error logging with context
- Product purchase flow logging
- Browser-compatible structured logging

## Log Structure

### Trace Correlation
Every log entry now includes:
```json
{
  "level": "info",
  "time": "2024-01-15T10:30:00.000Z",
  "traceId": "abc123def456...",
  "spanId": "789xyz...",
  "traceFlags": 1,
  "msg": "Coupon request received",
  "endpoint": "/coupons",
  "method": "GET"
}
```

### HTTP Request Logging
```json
{
  "level": "info",
  "time": "2024-01-15T10:30:00.000Z",
  "traceId": "abc123def456...",
  "httpRequest": {
    "method": "GET",
    "url": "/coupons",
    "statusCode": 200,
    "responseTime": 45
  },
  "msg": "HTTP GET /coupons - 200 (45ms)"
}
```

### External Service Calls
```json
{
  "level": "info",
  "time": "2024-01-15T10:30:00.000Z",
  "traceId": "abc123def456...",
  "externalCall": {
    "service": "membership-service",
    "method": "GET",
    "url": "/isMember",
    "statusCode": 200,
    "responseTime": 25,
    "success": true
  },
  "msg": "External call to membership-service GET /isMember - 200 (25ms)"
}
```

### Kafka Operations
```json
{
  "level": "info",
  "time": "2024-01-15T10:30:00.000Z",
  "traceId": "abc123def456...",
  "kafka": {
    "operation": "send",
    "topic": "coupon-applied",
    "success": true,
    "messageKey": "coupon",
    "processingTime": 15
  },
  "msg": "Kafka send on topic coupon-applied succeeded"
}
```

### Frontend/Browser Logs
```json
{
  "level": "info",
  "timestamp": "2024-01-15T10:30:00.000Z",
  "message": "Product purchase successful",
  "productId": "123",
  "responseTime": 250,
  "userAgent": "Mozilla/5.0...",
  "url": "https://example.com/products"
}
```

### Frontend API Call Logs
```json
{
  "level": "info",
  "timestamp": "2024-01-15T10:30:00.000Z",
  "message": "API POST /buy?id=123 - 200 (250ms)",
  "apiCall": {
    "method": "POST",
    "url": "/buy?id=123",
    "status": 200,
    "responseTime": 250,
    "success": true
  }
}
```

## Installation

To install the new dependencies:

```bash
# Coupon service
cd coupon && npm install

# Mail service (TypeScript)
cd mail && npm install && npm run build

# Webapp frontend - no additional dependencies needed
# (uses existing Next.js setup with custom logger utility)
```

## Configuration

### Environment Variables

#### Backend Services (Coupon & Mail)
- `LOG_LEVEL`: Set log level (default: 'info')
  - Options: 'trace', 'debug', 'info', 'warn', 'error', 'fatal'

#### Frontend (Webapp)
- `NEXT_PUBLIC_LOG_LEVEL`: Set log level for browser (default: 'info')
  - Options: 'trace', 'debug', 'info', 'warn', 'error', 'fatal'

### Example
```bash
# Backend services
export LOG_LEVEL=debug

# Frontend (Next.js environment variable)
export NEXT_PUBLIC_LOG_LEVEL=info
```

## Benefits

1. **Structured Logging**: JSON format enables better parsing and searching
2. **Trace Correlation**: Each log entry includes OpenTelemetry trace context
3. **Performance Monitoring**: Automatic timing for HTTP requests and external calls
4. **Better Debugging**: Structured error handling with proper context
5. **Observability**: Correlation between traces and logs for end-to-end visibility
6. **Production Ready**: Proper log levels and graceful shutdown handling

## Observability Integration

The new logging setup integrates with:
- **OpenTelemetry**: Automatic trace correlation
- **Log Aggregation**: JSON format ready for ELK stack, Splunk, etc.
- **APM Tools**: Trace ID correlation enables linking logs to distributed traces
- **Monitoring**: Structured data enables better alerting and dashboards

## Next Steps

1. **Install Dependencies**: Run the installation commands for both backend services
2. **OpenTelemetry Setup**: Ensure OpenTelemetry instrumentation is properly configured and enabled
3. **Log Aggregation**: Configure your log aggregation system (ELK, Splunk, etc.) to collect JSON logs
4. **Trace Correlation**: Set up dashboards to correlate traces with logs using `traceId` field
5. **Monitoring & Alerts**: Configure alerts based on structured log data and error rates
6. **Client-side Logging**: Consider implementing proper client-side logging infrastructure for production
7. **Testing**: Test the correlation between traces and logs across service boundaries

## Verification

To verify the logging setup is working correctly:

1. **Start the services** with OpenTelemetry instrumentation
2. **Make HTTP requests** to the coupon service
3. **Check logs** contain `traceId`, `spanId`, and proper structured data
4. **Verify correlation** between logs and traces in your observability platform