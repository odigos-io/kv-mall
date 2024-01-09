package pkg

import (
	"context"
	"github.com/keyval-dev/test-apps/kv-mall/analytics/persisters"
	"github.com/keyval-dev/test-apps/kv-mall/analytics/pkg/messagequeue"
	"log/slog"
	"time"
)

type Manager struct {
	persisters []persisters.Persister
	mq         messagequeue.MessageQueue
}

func NewManager() *Manager {
	return &Manager{}
}

func (m *Manager) WithPersisters(persisters ...persisters.Persister) *Manager {
	m.persisters = persisters
	return m
}

func (m *Manager) WithMessageQueue(mq messagequeue.MessageQueue) *Manager {
	m.mq = mq
	return m
}

func (m *Manager) Run() {
	for {
		msgs, err := m.mq.ReadMessages(context.Background())
		if err != nil {
			slog.Info("failed to read messages", "error", err)
			continue
		}

		slog.Info("read messages", "count", len(msgs))
		for _, msg := range msgs {
			data, err := m.mq.DeserializeMessage(msg)
			if err != nil {
				slog.Info("failed to deserialize message", "error", err)
			}

			slog.Info("persisting data in parallel", "data", string(data))
			t0 := time.Now()
			err = m.PersistParallel(data)
			if err != nil {
				slog.Info("failed to persist data", "error", err)
			}
			slog.Info("persisted data in parallel", "time", time.Since(t0).String())
		}
	}
}

func (m *Manager) Persist(data []byte) error {
	for _, p := range m.persisters {
		err := p.Persist(data)
		if err != nil {
			return err
		}
	}

	return nil
}

func (m *Manager) PersistParallel(data []byte) error {
	errs := make(chan error, len(m.persisters))
	for _, p := range m.persisters {
		go func(p persisters.Persister) {
			errs <- p.Persist(data)
		}(p)
	}

	for i := 0; i < len(m.persisters); i++ {
		err := <-errs
		if err != nil {
			return err
		}
	}

	return nil
}

func (m *Manager) Close() {
	for _, p := range m.persisters {
		p.Close()
	}
}
