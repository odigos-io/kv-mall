const { Kafka, Message } = require('kafkajs');
const express = require('express');
const pinoHttp = require('pino-http');
const { logger, logHttpRequest, logExternalCall, logKafkaOperation } = require('./logger');

const kafkaAddress = process.env.KAFKA_ADDRESS;
if(!kafkaAddress) {
  throw new Error('KAFKA_ADDRESS environment variable is not defined');
}

const PORT = parseInt(process.env.PORT || '8080');
const app = express();
const axios = require('axios');

// Add pino-http middleware for automatic HTTP request/response logging
app.use(pinoHttp({ logger }));

const kafka = new Kafka({
  clientId: "coupon-service",
  brokers: [kafkaAddress],
});
const kafkaProducer = kafka.producer();

function getRandomNumber(min, max) {
  return Math.floor(Math.random() * (max - min) + min);
}

app.get('/coupons', async (req, res) => {
  const startTime = Date.now();
  logger.info({ 
    endpoint: '/coupons',
    method: 'GET',
    userAgent: req.headers['user-agent'],
    ip: req.ip
  }, 'Coupon request received');

  try {
    // Fetch from membership api
    const externalCallStart = Date.now();
    const response = await axios.get(`http://${process.env.MEMBERSHIP_SERVICE_URL}/isMember`);
    const externalCallTime = Date.now() - externalCallStart;
    
    logExternalCall('membership-service', 'GET', '/isMember', response.status, externalCallTime, true);
    
    const couponValue = getRandomNumber(1, 25);
    const responseData = {
      coupon: couponValue,
      isMember: response.data.isMember
    };
    
    const totalTime = Date.now() - startTime;
    logger.info({
      endpoint: '/coupons',
      response: responseData,
      processingTime: totalTime
    }, 'Coupon request completed successfully');
    
    res.json(responseData);
  } catch (error) {
    const totalTime = Date.now() - startTime;
    logger.error({
      endpoint: '/coupons',
      err: error,
      processingTime: totalTime
    }, 'Coupon request failed');
    
    res.status(500).json({ error: 'Internal server error' });
  }
});

app.post('/apply-coupon', async (req, res) => {
  const startTime = Date.now();
  logger.info({
    endpoint: '/apply-coupon',
    method: 'POST',
    userAgent: req.headers['user-agent'],
    ip: req.ip
  }, 'Apply coupon request received');

  try {
    const externalCallStart = Date.now();
    const memberRes = await axios.get(`http://${process.env.MEMBERSHIP_SERVICE_URL}/isMember`);
    const externalCallTime = Date.now() - externalCallStart;
    
    logExternalCall('membership-service', 'GET', '/isMember', memberRes.status, externalCallTime, true);
    
    const couponValue = getRandomNumber(1, 25);
    const responseData = {
      coupon: couponValue,
      isMember: memberRes.data.isMember
    };
    
    res.json(responseData);
    
    // simulate sending email about this coupon being applied
    // by queuing a message to kafka and consuming it in mail service
    const kafkaStart = Date.now();
    await kafkaProducer.send({
      topic: 'coupon-applied',
      messages: [
        { key: 'coupon', value: JSON.stringify({ isMember: memberRes.data.isMember, couponValue }) },
      ],
    });
    const kafkaTime = Date.now() - kafkaStart;
    
    logKafkaOperation('send', 'coupon-applied', true, null, { 
      messageKey: 'coupon',
      processingTime: kafkaTime
    });
    
    const totalTime = Date.now() - startTime;
    logger.info({
      endpoint: '/apply-coupon',
      response: responseData,
      processingTime: totalTime
    }, 'Apply coupon request completed successfully');
    
  } catch (error) {
    const totalTime = Date.now() - startTime;
    logger.error({
      endpoint: '/apply-coupon',
      err: error,
      processingTime: totalTime
    }, 'Apply coupon request failed');
    
    res.status(500).json({ error: 'Internal server error' });
  }
});

process.on('SIGTERM', () => {
  logger.info('Received SIGTERM. Shutting down server gracefully');
  app.close(() => {
    logger.info('HTTP server shut down');
    process.exit(0);
  });
});

// in real production clusters, kafka is already available.
// in this demo, we install kafka and the business logic at the same time.
// thus we wait for kafka which is not common in real applications.
async function connectKafkaWithRetry() {
  let retryCount = 0;
  while (true) {
    try {
      await kafkaProducer.connect();
      logKafkaOperation('connect', 'producer', true, null, { retryCount });
      break;
    } catch (e) {
      retryCount++;
      logger.error({ 
        err: e, 
        retryCount,
        retryDelay: 5000
      }, 'Error connecting to Kafka, retrying in 5 seconds');
      await new Promise(resolve => setTimeout(resolve, 5000));
    }
  }
}

(async () => {
  try {
    await connectKafkaWithRetry();
    app.listen(PORT, () => {
      logger.info({ 
        port: PORT,
        service: 'coupon-service',
        nodeVersion: process.version
      }, `Coupon service listening on port ${PORT}`);
    });
  } catch (error) {
    logger.fatal({ err: error }, 'Failed to start coupon service');
    process.exit(1);
  }
})();
