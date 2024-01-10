package persisters

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"github.com/Azure/azure-sdk-for-go/sdk/azcore"
	"github.com/Azure/azure-sdk-for-go/sdk/data/azcosmos"
	"github.com/google/uuid"
	"log/slog"
	"os"
)

type Cosmos struct {
	client          *azcosmos.Client
	dbClient        *azcosmos.DatabaseClient
	containerClient *azcosmos.ContainerClient
}

func IsCosmosAvailable() bool {
	return os.Getenv("COSMOS_KEY") != ""
}

func NewCosmos() (*Cosmos, error) {
	host := getCosmosHost()
	key, exists := os.LookupEnv("COSMOS_KEY")
	if !exists {
		return nil, fmt.Errorf("COSMOS_KEY environment variable not set")
	}

	creds, err := azcosmos.NewKeyCredential(key)
	if err != nil {
		return nil, err
	}

	client, err := azcosmos.NewClientWithKey(host, creds, nil)
	if err != nil {
		return nil, err
	}

	// Create analytics database if it doesn't exist
	db, err := client.NewDatabase("analytics")
	if err != nil {
		return nil, err
	}

	cc, err := db.NewContainer("events")
	if err != nil {
		return nil, err
	}

	slog.Info("Connected to cosmos")
	return &Cosmos{
		client:          client,
		dbClient:        db,
		containerClient: cc,
	}, nil
}

func getCosmosHost() string {
	val, exists := os.LookupEnv("COSMOS_HOST")
	if !exists {
		return "https://kvmall.documents.azure.com:443/"
	}

	return val
}

func (c *Cosmos) Persist(data []byte) error {
	slog.Info("persisting data to cosmos")
	ctx := context.TODO()
	id := uuid.New().String()
	pk := azcosmos.NewPartitionKeyString(id)
	itemOptions := azcosmos.ItemOptions{
		ConsistencyLevel: azcosmos.ConsistencyLevelSession.ToPtr(),
	}

	jsonStruct := struct {
		ID          string `json:"id"`
		payloadSize int    `json:"payload_size"`
	}{
		ID:          id,
		payloadSize: len(data),
	}

	bytes, err := json.Marshal(jsonStruct)
	if err != nil {
		return err
	}

	_, err = c.containerClient.CreateItem(ctx, pk, bytes, &itemOptions)
	return err
}

func (c *Cosmos) Close() {
	slog.Info("closing cosmos connection")
}

func (c *Cosmos) SetupDB() error {
	ctx := context.TODO()

	// Create database if it doesn't exist
	dbProperties := azcosmos.DatabaseProperties{
		ID: "analytics",
	}
	_, err := c.client.CreateDatabase(ctx, dbProperties, nil)
	if ignoreAlreadyExistsError(err) != nil {
		return err
	}

	// Create events container if it doesn't exist
	containerProperties := azcosmos.ContainerProperties{
		ID: "events",
		PartitionKeyDefinition: azcosmos.PartitionKeyDefinition{
			Paths: []string{"/id"},
		},
	}

	throughputProperties := azcosmos.NewManualThroughputProperties(400) //defaults to 400 if not set
	options := &azcosmos.CreateContainerOptions{
		ThroughputProperties: &throughputProperties,
	}
	_, err = c.dbClient.CreateContainer(ctx, containerProperties, options)
	return ignoreAlreadyExistsError(err)
}

func ignoreAlreadyExistsError(err error) error {
	if err == nil {
		return nil
	}

	var responseErr *azcore.ResponseError
	if errors.As(err, &responseErr) && responseErr.StatusCode == 409 {
		return nil
	}

	return err
}
