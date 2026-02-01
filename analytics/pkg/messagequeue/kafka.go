package messagequeue

import (
	"context"
	"fmt"
	"os"
	"time"

	"github.com/segmentio/kafka-go"
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
	if k.reader != nil {
		k.reader.Close()
	}

	k.reader = kafka.NewReader(kafka.ReaderConfig{
		Brokers:  []string{k.getKafkaAddress()},
		Topic:    "analytics",
		GroupID:  "analytics",
		MaxBytes: 10e6, // 10MB
	})

	// Actually verify the connection by trying to fetch metadata
	// This ensures Kafka is ready and the topic exists
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	conn, err := kafka.DialContext(ctx, "tcp", k.getKafkaAddress())
	if err != nil {
		return fmt.Errorf("failed to dial kafka: %w", err)
	}
	defer conn.Close()

	// Verify the topic exists
	partitions, err := conn.ReadPartitions("analytics")
	if err != nil {
		return fmt.Errorf("failed to read partitions: %w", err)
	}

	if len(partitions) == 0 {
		return fmt.Errorf("topic 'analytics' has no partitions")
	}

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
