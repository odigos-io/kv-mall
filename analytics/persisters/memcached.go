package persisters

import (
	"github.com/bradfitz/gomemcache/memcache"
	"log/slog"
	"os"
)

type Memcached struct {
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

	return &Memcached{mc: mc}, nil
}

func (m *Memcached) Persist(data []byte) error {
	slog.Info("persisting data to memcached")
	return m.mc.Set(&memcache.Item{Key: "foo", Value: data})
}

func (m *Memcached) Close() {
	m.mc.Close()
}

func (m *Memcached) SetupDB() error {
	return nil
}
