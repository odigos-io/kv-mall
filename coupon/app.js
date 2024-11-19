const { Kafka, Message } = require('kafkajs');
const express = require('express');

const kafkaAddress = process.env.KAFKA_ADDRESS;
if(!kafkaAddress) {
  throw new Error('KAFKA_ADDRESS environment variable is not defined');
}

const PORT = parseInt(process.env.PORT || '8080');
const app = express();
const axios = require('axios');

const kafka = new Kafka({
  clientId: "coupon-service",
  brokers: [kafkaAddress],
});
const kafkaProducer = kafka.producer();

function getRandomNumber(min, max) {
  return Math.floor(Math.random() * (max - min) + min);
}

app.get('/coupons', (req, res) => {
  console.log('Coupon request received!');

  // Fetch from membership api
  axios.get(`http://${process.env.MEMBERSHIP_SERVICE_URL}/isMember`)
    .then(function (response) {
      console.log('Got response from membership service!', response.data);
      res.json({
        coupon: getRandomNumber(1, 25),
        isMember: response.data.isMember
      });
    })
    .catch(function (error) {
      console.log(error);
    });
});

app.post('/apply-coupon', async (req, res) => {
  console.log('Applying coupon!');

  try {
    const memberRes = await axios.get(`http://${process.env.MEMBERSHIP_SERVICE_URL}/isMember`);
    console.log('Got response from membership service!', memberRes.data);
    res.json({
      coupon: getRandomNumber(1, 25),
      isMember: memberRes.data.isMember
    });
    // simulate sending email about this coupon being applied
    // by queuing a message to kafka and consuming it in mail service
    await kafkaProducer.send({
      topic: 'coupon-applied',
      messages: [
        { key: 'coupon', value: JSON.stringify({ isMember: memberRes.data.isMember }) },
      ],
    });
  } catch (error) {
    console.log(error);
  }
});

process.on('SIGTERM', () => {
  console.log('Received SIGTERM. Shutting down server.');
  app.close(() => {
    console.log('Server shut down.');
    process.exit(0);
  });
});

// in real production clusters, kafka is already available.
// in this demo, we install kafka and the business logic at the same time.
// thus we wait for kafka which is not common in real applications.
async function connectKafkaWithRetry() {
  while (true) {
    try {
      await kafkaProducer.connect();
      console.log('Connected to Kafka!');
      break;
    } catch (e) {
      console.error('Error connecting to Kafka, retrying in 5 seconds:', e);
      await new Promise(resolve => setTimeout(resolve, 5000));
    }
  }
}

(async () => {
  await connectKafkaWithRetry();
  app.listen(PORT, () => {
    console.log(`Listening for requests on port ${PORT}`);
  });
})();
