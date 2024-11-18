import { Kafka, EachMessagePayload } from "kafkajs";

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
    while (true) {
      try {
        await consumer.connect();
        console.log('Connected to Kafka!');
        break;
      } catch (e) {
        console.error('Error connecting to Kafka, retrying in 5 seconds:', e);
        await new Promise(resolve => setTimeout(resolve, 5000));
      }
    }
  }
  
const run = async (): Promise<void> => {
  const topic = "coupon-applied";
  try {
    await connectKafkaWithRetry();
    await consumer.subscribe({ topics: [topic] });
    await consumer.run({
      eachMessage: async ({ message }: EachMessagePayload) => {
        console.log("mock sending email to user ");
        console.log(message.value?.toString());
      },
    });
    console.log("Consumer is ready and listening for messages!");
  } catch (error) {
    console.error("Error in consumer: ", error);
  }
};

process.on("SIGTERM", async () => {
  console.log("Received SIGTERM. Shutting down server.");
  await consumer.disconnect();
  console.log('Kafka consumer disconnected');
});

run();
