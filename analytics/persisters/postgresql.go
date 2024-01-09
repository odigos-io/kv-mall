package persisters

import (
	"database/sql"
	_ "github.com/lib/pq"
	"log/slog"
	"os"
)

type Postgresql struct {
	client *sql.DB
}

func NewPostgresql() (*Postgresql, error) {
	addr, exists := os.LookupEnv("POSTGRESQL_ADDR")
	if !exists {
		addr = "localhost:5432"
	}

	db, err := sql.Open("postgres", addr)
	if err != nil {
		return nil, err
	}

	slog.Info("Connected to postgresql")
	return &Postgresql{
		client: db,
	}, nil
}

func (p *Postgresql) Persist(data []byte) error {
	slog.Info("persisting data to postgresql")
	_, err := p.client.Exec("INSERT INTO analytics (message) VALUES ($1)", data)
	return err
}

func (p *Postgresql) Close() {
	p.client.Close()
}

func (p *Postgresql) SetupDB() error {
	_, err := p.client.Exec("CREATE TABLE IF NOT EXISTS analytics (id SERIAL PRIMARY KEY, message text)")
	return err
}
