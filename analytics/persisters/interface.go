package persisters

type Persister interface {
	// Persist persists the given data.
	Persist(data []byte) error
	Close()
}

type Creator func() (Persister, error)
