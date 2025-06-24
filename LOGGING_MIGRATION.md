# Logging Migration: Console.log to Pino with Runtime Agent Correlation

## Overview

This document describes the migration of NodeJS applications from `console.log` to structured logging using **Pino**. The correlation between logs and distributed traces will be handled automatically by a **runtime observability agent** (such as OpenTelemetry auto-instrumentation), so no observability-specific code is needed in the application.

## Changes Made

### 1. Dependencies Added

#### Coupon Service (`coupon/package.json`)
- `pino`: ^8.15.0 - Fast JSON logger for Node.js
- `pino-http`: ^8.5.0 - HTTP request logging middleware

#### Mail Service (`mail/package.json`)
- `pino`: ^8.15.0 - Fast JSON logger for Node.js

#### Webapp Frontend (`webapp/`)
- Custom browser-compatible logger utility created (`utils/logger.js`)
- No additional dependencies required (uses existing Next.js setup)

### 2. Logger Utilities Created

#### Coupon Service (`coupon/logger.js`)
- **Structured Logging**: JSON format with proper serializers for requests, responses, and errors
- **Helper Functions**:
  - `logHttpRequest()`: Log HTTP requests with timing and status codes
  - `logExternalCall()`: Log calls to external services (membership service)
  - `logKafkaOperation()`: Log Kafka producer operations

#### Mail Service (`mail/logger.ts`)
- **TypeScript Support**: Proper type definitions for structured logging
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

### Standard Application Logs
Structured JSON logs ready for correlation by runtime agent:
```json
{
  "level": "info",
  "time": "2024-01-15T10:30:00.000Z",
  "msg": "Coupon request received",
  "endpoint": "/coupons",
  "method": "GET",
  "userAgent": "Mozilla/5.0...",
  "ip": "192.168.1.1"
}
```

### HTTP Request Logging
```json
{
  "level": "info",
  "time": "2024-01-15T10:30:00.000Z",
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
2. **Runtime Correlation**: Logs will be automatically correlated with traces by observability agent
3. **Performance Monitoring**: Automatic timing for HTTP requests and external calls
4. **Better Debugging**: Structured error handling with proper context
5. **Agent-Based Observability**: No application code changes needed for trace correlation
6. **Production Ready**: Proper log levels and graceful shutdown handling

## Observability Integration

The new logging setup integrates with:
- **Runtime Agents**: OpenTelemetry auto-instrumentation or similar agents handle correlation
- **Log Aggregation**: JSON format ready for ELK stack, Splunk, etc.
- **APM Tools**: Agent-based correlation links logs to distributed traces automatically
- **Monitoring**: Structured data enables better alerting and dashboards

## How Runtime Correlation Works

1. **Observability Agent**: Deployed alongside your application (as sidecar, agent, or library)
2. **Automatic Instrumentation**: Agent automatically instruments HTTP, database, and messaging calls
3. **Context Propagation**: Agent handles trace context propagation across service boundaries
4. **Log Correlation**: Agent automatically injects trace IDs into log records at runtime
5. **No Code Changes**: Application code remains clean and focused on business logic

## Next Steps

1. **Install Dependencies**: Run the installation commands for both backend services
2. **Deploy with Agent**: Deploy services with your observability agent (OpenTelemetry, Datadog, etc.)
3. **Configure Agent**: Ensure agent is configured to inject trace context into logs
4. **Log Aggregation**: Configure your log aggregation system to collect JSON logs
5. **Monitoring & Alerts**: Configure alerts based on structured log data and error rates
6. **Client-side Logging**: Consider implementing proper client-side logging infrastructure for production
7. **Testing**: Test the correlation between traces and logs across service boundaries

## Verification

To verify the logging setup is working correctly:

1. **Start the services** with your observability agent
2. **Make HTTP requests** to the coupon service
3. **Check logs** contain proper structured data and agent-injected trace context
4. **Verify correlation** between logs and traces in your observability platform