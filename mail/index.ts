import { Kafka, EachMessagePayload } from "kafkajs";
import { logger, logKafkaOperation, logMessageProcessing } from "./logger";

const kafkaAddress = process.env["KAFKA_ADDRESS"];
if (!kafkaAddress) {
  throw new Error("KAFKA_ADDRESS environment variable is not defined");
}

// Define the Kafka client and broker
const kafka = new Kafka({
  clientId: "mail-service",
  brokers: [kafkaAddress],
});

// Create a consumer
const consumer = kafka.consumer({ groupId: "send-mail-on-apply-coupon" });

// in real production clusters, kafka is already available.
// in this demo, we install kafka and the business logic at the same time.
// thus we wait for kafka which is not common in real applications.
async function connectKafkaWithRetry() {
  let retryCount = 0;
  while (true) {
    try {
      await consumer.connect();
      logKafkaOperation('connect', 'consumer', true, null, { retryCount });
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

const run = async (): Promise<void> => {
  const topic = "coupon-applied";
  try {
    await connectKafkaWithRetry();
    
    logger.info({ topic }, `Subscribing to topic: ${topic}`);
    await consumer.subscribe({ topics: [topic] });
    
    await consumer.run({
      eachMessage: async ({ topic, partition, message }: EachMessagePayload) => {
        const startTime = Date.now();
        const messageKey = message.key?.toString() || null;
        const messageValue = message.value?.toString() || null;
        
        logger.info({
          kafka: {
            topic,
            partition,
            offset: message.offset,
            messageKey,
            hasValue: !!messageValue,
            timestamp: message.timestamp
          }
        }, 'Processing Kafka message');

        try {
          // Simulate email sending processing
          logger.info({
            email: {
              action: 'sending',
              recipient: 'user',
              template: 'coupon-applied'
            },
            messageData: messageValue ? JSON.parse(messageValue) : null
          }, 'Sending email notification to user');
          
          // Simulate some processing time
          await new Promise(resolve => setTimeout(resolve, 100));
          
          const processingTime = Date.now() - startTime;
          logMessageProcessing(topic, messageKey, messageValue, true, null, processingTime);
          
        } catch (error) {
          const processingTime = Date.now() - startTime;
          logMessageProcessing(topic, messageKey, messageValue, false, error as Error, processingTime);
        }
      },
    });
    
    logger.info({ 
      topic,
      consumerGroup: 'send-mail-on-apply-coupon',
      service: 'mail-service'
    }, "Mail service consumer is ready and listening for messages");
    
  } catch (error) {
    logger.error({ 
      err: error,
      topic 
    }, "Error in mail service consumer");
    throw error;
  }
};

process.on("SIGTERM", async () => {
  logger.info("Received SIGTERM. Shutting down mail service gracefully");
  try {
    await consumer.disconnect();
    logger.info('Kafka consumer disconnected successfully');
  } catch (error) {
    logger.error({ err: error }, 'Error disconnecting Kafka consumer');
  }
  process.exit(0);
});

// Start the service
(async () => {
  try {
    logger.info({
      service: 'mail-service',
      nodeVersion: process.version,
      kafkaAddress
    }, 'Starting mail service');
    
    await run();
  } catch (error) {
    logger.fatal({ err: error }, 'Failed to start mail service');
    process.exit(1);
  }
})();
