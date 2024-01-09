package main

import (
	"github.com/keyval-dev/test-apps/kv-mall/analytics/persisters"
	"github.com/keyval-dev/test-apps/kv-mall/analytics/pkg"
	"github.com/keyval-dev/test-apps/kv-mall/analytics/pkg/messagequeue"
	"log"
	"os"
	"os/signal"
	"syscall"
)

func main() {
	log.Println("Starting analytics service")
	kafka := &messagequeue.KafkaMessageQueue{}
	if err := kafka.Connect(); err != nil {
		log.Fatal("failed to connect to kafka: ", err)
	}

	memcached, err := persisters.NewMemcached()
	if err != nil {
		log.Fatal("failed to connect to memcached: ", err)
	}

	cassandra, err := persisters.NewCassandra()
	if err != nil {
		log.Fatal("failed to connect to cassandra: ", err)
	}

	if err := cassandra.SetupDB(); err != nil {
		log.Fatal("failed to setup cassandra: ", err)
	}

	pg, err := persisters.NewPostgresql()
	if err != nil {
		log.Fatal("failed to connect to postgresql: ", err)
	}

	if err := pg.SetupDB(); err != nil {
		log.Fatal("failed to setup postgresql: ", err)
	}

	cosmos, err := persisters.NewCosmos()
	if err != nil {
		log.Fatal("failed to connect to cosmos: ", err)
	}

	if err := cosmos.SetupDB(); err != nil {
		log.Fatal("failed to setup cosmos: ", err)
	}

	mgr := pkg.NewManager().
		WithPersisters(memcached, cassandra, pg, cosmos).
		WithMessageQueue(kafka)

	defer mgr.Close()
	go mgr.Run()

	signalChan := make(chan os.Signal, 1)
	signal.Notify(signalChan, os.Interrupt, syscall.SIGTERM)
	<-signalChan
	mgr.Close()
}
