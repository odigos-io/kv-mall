package main

import (
	"encoding/json"
	"github.com/segmentio/kafka-go"
	"log/slog"
	"net/http"
	"strconv"
)

const (
	httpPort = 8080
)

type IsMemberResponse struct {
	IsMember bool `json:"isMember"`
}

func main() {
	slog.Info("Starting Membership service", "port", httpPort)
	kafkaClient := NewKafkaClient()
	defer kafkaClient.Close()

	http.HandleFunc("/isMember", func(writer http.ResponseWriter, request *http.Request) {
		slog.Info("isMember called")
		res := IsMemberResponse{
			IsMember: true,
		}

		data, err := json.Marshal(res)
		if err != nil {
			slog.Error("failed to marshal response", "error", err)
			writer.WriteHeader(http.StatusInternalServerError)
			return
		}

		slog.Info("writing message to kafka")
		err = kafkaClient.WriteMessages(request.Context(), kafka.Message{
			Key:   []byte("isMember"),
			Value: data,
		}, kafka.Message{
			Key:   []byte("test-message"),
			Value: []byte("test-value"),
			Headers: []kafka.Header{
				{
					Key:   "test-header",
					Value: []byte("test-value"),
				},
			},
		})

		if err != nil {
			slog.Error("failed to write message to kafka", "error", err)
			writer.WriteHeader(http.StatusInternalServerError)
			return
		}

		_, err = writer.Write(data)
		if err != nil {
			slog.Error("failed to write response", "error", err)
			writer.WriteHeader(http.StatusInternalServerError)
			return
		}

	})

	err := http.ListenAndServe(":"+strconv.Itoa(httpPort), nil)
	if err != nil {
		slog.Error("failed to start Membership service", "error", err)
	}
}
