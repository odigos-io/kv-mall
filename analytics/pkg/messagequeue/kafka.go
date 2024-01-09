package messagequeue

import (
	"context"
	"fmt"
	"github.com/segmentio/kafka-go"
	"os"
)

type MessageQueue interface {
	Connect() error
	ReadMessages(ctx context.Context) ([]any, error)
	Close() error
	DeserializeMessage(msg any) ([]byte, error)
}

type KafkaMessageQueue struct {
	reader *kafka.Reader
}

func (k *KafkaMessageQueue) getKafkaAddress() string {
	val, ok := os.LookupEnv("KAFKA_ADDRESS")
	if !ok {
		return "127.0.0.1:9092"
	}

	return val
}

func (k *KafkaMessageQueue) Connect() error {
	k.reader = kafka.NewReader(kafka.ReaderConfig{
		Brokers:  []string{k.getKafkaAddress()},
		Topic:    "analytics",
		GroupID:  "analytics",
		MaxBytes: 10e6, // 10MB
	})

	return nil
}

func (k *KafkaMessageQueue) ReadMessages(ctx context.Context) ([]any, error) {
	msg, err := k.reader.ReadMessage(ctx)
	if err != nil {
		return nil, err
	}

	return []any{msg}, nil
}

func (k *KafkaMessageQueue) Close() error {
	return k.reader.Close()
}

func (k *KafkaMessageQueue) DeserializeMessage(msg any) ([]byte, error) {
	kMsg, ok := msg.(kafka.Message)
	if !ok {
		return nil, fmt.Errorf("invalid message type")
	}

	return kMsg.Value, nil
}
