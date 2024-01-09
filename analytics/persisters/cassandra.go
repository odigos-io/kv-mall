package persisters

import (
	"github.com/gocql/gocql"
	"log/slog"
	"os"
)

type Cassandra struct {
	session *gocql.Session
}

func NewCassandra() (*Cassandra, error) {
	addr, exists := os.LookupEnv("CASSANDRA_ADDR")
	if !exists {
		addr = "localhost:9042"
	}

	cluster := gocql.NewCluster(addr)
	session, err := cluster.CreateSession()
	if err != nil {
		return nil, err
	}

	slog.Info("Connected to cassandra")
	return &Cassandra{session: session}, nil
}

func (c *Cassandra) Persist(data []byte) error {
	slog.Info("persisting data to cassandra")
	return c.session.Query("INSERT INTO analytics.data (id, message) VALUES (?, ?)", gocql.TimeUUID(), data).Exec()
}

func (c *Cassandra) Close() {
	c.session.Close()
}

func (c *Cassandra) SetupDB() error {
	err := c.session.Query("CREATE KEYSPACE IF NOT EXISTS analytics WITH REPLICATION = {'class' : 'SimpleStrategy', 'replication_factor' : 1}").Exec()
	if err != nil {
		return err
	}

	return c.session.Query("CREATE TABLE IF NOT EXISTS analytics.data (id UUID,message text, PRIMARY KEY(id))").Exec()
}
