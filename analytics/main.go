package main

import (
	"context"
	"log"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/keyval-dev/test-apps/kv-mall/analytics/persisters"
	"github.com/keyval-dev/test-apps/kv-mall/analytics/pkg"
	"github.com/keyval-dev/test-apps/kv-mall/analytics/pkg/messagequeue"
)

var Healthy = false

func PollPersisterUntilReady(ctx context.Context, name string, createFunc persisters.Creator) persisters.Persister{
	ticker := time.NewTicker(1 * time.Second)
	defer ticker.Stop()

	for {
		select {
		case <-ctx.Done():
			return nil
		case <-ticker.C:
			if p, err := setupPersister(createFunc); err == nil {
				log.Println("Connected to ", name)
				return p
			} else {
				log.Println("failed to connect to ", name, ", retrying... ", err)
			}
		}
	}
}

func PollUntilMessageQueueReady(ctx context.Context, name string, mq messagequeue.MessageQueue) {
	ticker := time.NewTicker(1 * time.Second)
	defer ticker.Stop()

	for {
		select {
		case <-ctx.Done():
			return
		case <-ticker.C:
			if err := mq.Connect(); err == nil {
				log.Println("Connected to message queue ", name)
				return
			} else {
				log.Println("failed to connect to ", name, ", retrying... ", err)
			}
		}
	}

}

func setupPersister(createFunc persisters.Creator) (persisters.Persister, error) {
	p, err := createFunc()
	if err != nil {
		log.Println("failed to connect to cassandra, retrying... ", err)
		return nil, err
	}
	err = p.SetupDB()
	return p, err
}

type persisterCreator struct {
	creator persisters.Creator
	name string
}

func healthCheck(w http.ResponseWriter, r *http.Request) {
	if Healthy {
		w.WriteHeader(http.StatusOK)
	} else {
		w.WriteHeader(http.StatusServiceUnavailable)
	}
}

func main() {
	log.Println("Starting analytics service")

	// Setup health check for k8s
	http.HandleFunc("/health", healthCheck)
	go http.ListenAndServe(":8081", nil)

	ctx, cancel := context.WithCancel(context.Background())
	signalChan := make(chan os.Signal, 1)
	signal.Notify(signalChan, os.Interrupt, syscall.SIGTERM)
	defer func() {
		signal.Stop(signalChan)
		cancel()
	}()
	go func() {
		select {
		case <-signalChan:
			cancel()
		case <-ctx.Done():
		}
	}()
	

	creators := []persisterCreator{
		{
			creator: persisters.NewMemcached,
			name: "memcached",
		},
		{
			creator: persisters.NewCassandra,
			name: "cassandra",
		},
		{
			creator: persisters.NewPostgresql,
			name: "postgresql",
		},
	}

	if persisters.IsCosmosAvailable() {
		creators = append(creators, persisterCreator{
			creator: persisters.NewCosmos,
			name: "cosmos",
		})
	} else {
		log.Println("Cosmos not available, skipping")
	}

	persistersChan := make(chan persisters.Persister, len(creators))
	for _, creator := range creators {
		go func(c persisterCreator) {
			persistersChan <- PollPersisterUntilReady(ctx, c.name, c.creator)
		}(creator)
	}

	var pers []persisters.Persister
	for p := range persistersChan {
		pers = append(pers, p)
		if len(pers) == len(creators) {
			close(persistersChan)
		}
	}

	kafka := &messagequeue.KafkaMessageQueue{}
	PollUntilMessageQueueReady(ctx, "kafka", kafka)

	Healthy = true

	mgr := pkg.NewManager().
		WithPersisters(pers...).
		WithMessageQueue(kafka)

	defer mgr.Close()
	mgr.Run(ctx)
}
