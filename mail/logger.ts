import pino from 'pino';
import { trace } from '@opentelemetry/api';

// Create pino logger with custom serializers and formatters
const logger = pino({
  level: (process.env.LOG_LEVEL as pino.Level) || 'info',
  formatters: {
    level: (label: string) => {
      return { level: label };
    },
    log: (object: any) => {
      // Add OpenTelemetry trace context to every log
      const span = trace.getActiveSpan();
      if (span) {
        const spanContext = span.spanContext();
        object.traceId = spanContext.traceId;
        object.spanId = spanContext.spanId;
        object.traceFlags = spanContext.traceFlags;
      }
      return object;
    }
  },
  serializers: {
    err: pino.stdSerializers.err
  }
});

// Helper function to get child logger with additional context
const getChildLogger = (context: Record<string, any> = {}) => {
  return logger.child(context);
};

// Helper function to log Kafka operations
const logKafkaOperation = (
  operation: string, 
  topic: string, 
  success: boolean = true, 
  error?: Error | null, 
  additionalData: Record<string, any> = {}
) => {
  const logData: any = {
    kafka: {
      operation,
      topic,
      success,
      ...additionalData
    }
  };
  
  if (error) {
    logData.err = error;
  }
  
  if (success) {
    logger.info(logData, `Kafka ${operation} on topic ${topic} succeeded`);
  } else {
    logger.error(logData, `Kafka ${operation} on topic ${topic} failed`);
  }
};

// Helper function to log message processing
const logMessageProcessing = (
  topic: string,
  messageKey: string | null,
  messageValue: string | null,
  success: boolean = true,
  error?: Error | null,
  processingTime?: number
) => {
  const logData: any = {
    messageProcessing: {
      topic,
      messageKey,
      hasValue: !!messageValue,
      success,
      processingTime
    }
  };
  
  if (error) {
    logData.err = error;
  }
  
  if (success) {
    logger.info(logData, `Message processed successfully from topic ${topic}`);
  } else {
    logger.error(logData, `Message processing failed from topic ${topic}`);
  }
};

export {
  logger,
  getChildLogger,
  logKafkaOperation,
  logMessageProcessing
};