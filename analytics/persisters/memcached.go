package persisters

import (
	"github.com/bradfitz/gomemcache/memcache"
	"log/slog"
	"os"
)

type memcached struct {
	mc *memcache.Client
}

func NewMemcached() (Persister, error) {
	addr, exists := os.LookupEnv("MEMCACHED_ADDR")
	if !exists {
		addr = "localhost:11211"
	}

	mc := memcache.New(addr)
	err := mc.Ping()
	if err != nil {
		return nil, err
	}

	slog.Info("Connected to memcached")
	return &memcached{mc: mc}, nil
}

func (m *memcached) Persist(data []byte) error {
	slog.Info("persisting data to memcached")
	return m.mc.Set(&memcache.Item{Key: "foo", Value: data})
}

func (m *memcached) Close() {
	m.mc.Close()
}
