package main

import (
	"context"
	"github.com/segmentio/kafka-go"

	"os"
)

type KafkaMessageQueue struct {
	writer *kafka.Writer
}

func getKafkaAddress() string {
	val, ok := os.LookupEnv("KAFKA_ADDRESS")
	if !ok {
		return "127.0.0.1:9092"
	}

	return val
}

func NewKafkaClient() *KafkaMessageQueue {
	return &KafkaMessageQueue{
		writer: &kafka.Writer{
			Addr:  kafka.TCP(getKafkaAddress()),
			Topic: "analytics",
		},
	}
}

func (k *KafkaMessageQueue) WriteMessages(ctx context.Context, msgs ...kafka.Message) error {
	return k.writer.WriteMessages(ctx, msgs...)
}

func (k *KafkaMessageQueue) Close() error {
	return k.writer.Close()
}
