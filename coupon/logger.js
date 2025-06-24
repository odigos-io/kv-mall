const pino = require('pino');

// Create pino logger with custom serializers and formatters
const logger = pino({
  level: process.env.LOG_LEVEL || 'info',
  formatters: {
    level: (label) => {
      return { level: label };
    }
  },
  serializers: {
    req: pino.stdSerializers.req,
    res: pino.stdSerializers.res,
    err: pino.stdSerializers.err
  }
});

// Helper function to get child logger with additional context
const getChildLogger = (context = {}) => {
  return logger.child(context);
};

// Helper function to log HTTP requests
const logHttpRequest = (method, url, statusCode, responseTime, additionalData = {}) => {
  logger.info({
    httpRequest: {
      method,
      url,
      statusCode,
      responseTime
    },
    ...additionalData
  }, `HTTP ${method} ${url} - ${statusCode} (${responseTime}ms)`);
};

// Helper function to log external service calls
const logExternalCall = (service, method, url, statusCode, responseTime, success = true) => {
  const logData = {
    externalCall: {
      service,
      method,
      url,
      statusCode,
      responseTime,
      success
    }
  };
  
  if (success) {
    logger.info(logData, `External call to ${service} ${method} ${url} - ${statusCode} (${responseTime}ms)`);
  } else {
    logger.error(logData, `External call to ${service} failed ${method} ${url} - ${statusCode} (${responseTime}ms)`);
  }
};

// Helper function to log Kafka operations
const logKafkaOperation = (operation, topic, success = true, error = null, additionalData = {}) => {
  const logData = {
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

module.exports = {
  logger,
  getChildLogger,
  logHttpRequest,
  logExternalCall,
  logKafkaOperation
};