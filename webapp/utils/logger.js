// Simple browser-compatible logger utility
// For production, consider using a proper client-side logging solution

const LOG_LEVELS = {
  trace: 0,
  debug: 1,
  info: 2,
  warn: 3,
  error: 4,
  fatal: 5
};

const currentLogLevel = LOG_LEVELS[process.env.NEXT_PUBLIC_LOG_LEVEL || 'info'];

const formatLogMessage = (level, message, data = {}) => {
  return {
    level,
    timestamp: new Date().toISOString(),
    message,
    ...data,
    // In a browser environment, we don't have access to OpenTelemetry trace context
    // This would typically be handled by client-side instrumentation
    userAgent: typeof window !== 'undefined' ? window.navigator.userAgent : undefined,
    url: typeof window !== 'undefined' ? window.location.href : undefined
  };
};

const log = (level, message, data = {}) => {
  if (LOG_LEVELS[level] >= currentLogLevel) {
    const logEntry = formatLogMessage(level, message, data);
    
    // In development, use console methods for better browser dev tools integration
    if (process.env.NODE_ENV === 'development') {
      switch (level) {
        case 'trace':
        case 'debug':
          console.debug('[DEBUG]', logEntry);
          break;
        case 'info':
          console.info('[INFO]', logEntry);
          break;
        case 'warn':
          console.warn('[WARN]', logEntry);
          break;
        case 'error':
        case 'fatal':
          console.error('[ERROR]', logEntry);
          break;
        default:
          console.log(logEntry);
      }
    } else {
      // In production, use structured JSON logging
      console.log(JSON.stringify(logEntry));
    }
  }
};

const logger = {
  trace: (message, data) => log('trace', message, data),
  debug: (message, data) => log('debug', message, data),
  info: (message, data) => log('info', message, data),
  warn: (message, data) => log('warn', message, data),
  error: (message, data) => log('error', message, data),
  fatal: (message, data) => log('fatal', message, data),
};

// Helper function to log API calls
const logApiCall = (method, url, status, responseTime, success = true, errorData = null) => {
  const logData = {
    apiCall: {
      method,
      url,
      status,
      responseTime,
      success
    }
  };
  
  if (errorData) {
    logData.error = errorData;
  }
  
  if (success) {
    logger.info(`API ${method} ${url} - ${status} (${responseTime}ms)`, logData);
  } else {
    logger.error(`API ${method} ${url} failed - ${status} (${responseTime}ms)`, logData);
  }
};

export { logger, logApiCall };
export default logger;