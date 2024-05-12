package main

import (
	"context"
	"fmt"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"
)

const (
	FrontendURL = "http://frontend:8080"

	DefaultBuyProductInterval = 2 * time.Second
	BuyProductIntervalEnv = "BUY_PRODUCT_INTERVAL"

	DefaultGetProductsInterval = 10 * time.Second
	GetProductsIntervalEnv = "GET_PRODUCTS_INTERVAL"

	MaxProductID = 20
	MinProductID = 1
)


type LoadGenerator struct {
	httpClient *http.Client
	buyProductInterval time.Duration
	getProductsInterval time.Duration
	lastProductID int
}

func (lg *LoadGenerator) applyEnvVars() {
	if buyProductIntervalEnv := os.Getenv(BuyProductIntervalEnv); buyProductIntervalEnv != "" {
		if duration, err := time.ParseDuration(buyProductIntervalEnv); err == nil {
			lg.buyProductInterval = duration
		}
	}

	if getProductsIntervalEnv := os.Getenv(GetProductsIntervalEnv); getProductsIntervalEnv != "" {
		if duration, err := time.ParseDuration(getProductsIntervalEnv); err == nil {
			lg.getProductsInterval = duration
		}
	}
}

func (lg *LoadGenerator) buyProduct(ctx context.Context, productID int) {
	buyURL := fmt.Sprintf("%s/buy?id=%d", FrontendURL, productID)
	req, err := http.NewRequestWithContext(ctx, "POST", buyURL, nil)
	if err != nil {
		fmt.Printf("Error creating request for buying product %d: %v\n", productID, err)
		return
	}

	resp, err :=lg.httpClient.Do(req)

	if err != nil {
		fmt.Printf("Error buying product %d: %v\n", productID, err)
		return
	}

	if resp.StatusCode != http.StatusOK {
		fmt.Printf("Status code buying product %d: %d\n", productID, resp.StatusCode)
	}
}

func (lg *LoadGenerator) getProducts(ctx context.Context) {
	getProductsURL := fmt.Sprintf("%s/products", FrontendURL)
	req, err := http.NewRequestWithContext(ctx, "GET", getProductsURL, nil)
	if err != nil {
		fmt.Printf("Error creating request for getting products: %v\n", err)
		return
	}

	resp, err := lg.httpClient.Do(req)

	if err != nil {
		fmt.Printf("Error getting products: %v\n", err)
		return
	}

	if resp.StatusCode != http.StatusOK {
		fmt.Printf("Status code getting products: %d\n", resp.StatusCode)
		return
	}
}

func (lg *LoadGenerator) run(ctx context.Context) {
	tickerBuyProduct := time.NewTicker(lg.buyProductInterval)
	defer tickerBuyProduct.Stop()
	tickerGetProducts := time.NewTicker(lg.getProductsInterval)
	defer tickerGetProducts.Stop()

	for {
		select {
		case <-ctx.Done():
			return
		case <-tickerBuyProduct.C:
			go lg.buyProduct(ctx, lg.lastProductID)
			lg.lastProductID++
			if lg.lastProductID > MaxProductID {
				lg.lastProductID = MinProductID
			}
		case <-tickerGetProducts.C:
			go lg.getProducts(ctx)
		}
	}
}

func main() {
	client := &http.Client{}
	loadGenerator := &LoadGenerator{
		httpClient: client,
		buyProductInterval: DefaultBuyProductInterval,
		getProductsInterval: DefaultGetProductsInterval,
		lastProductID: MinProductID,
	}

	loadGenerator.applyEnvVars()

	fmt.Printf("load-generator started with buyProductInterval=%v and getProductsInterval=%v\n", loadGenerator.buyProductInterval, loadGenerator.getProductsInterval)

	// Trap Ctrl+C and SIGTERM and call cancel on the context.
	ctx, cancel := context.WithCancel(context.Background())
	ch := make(chan os.Signal, 1)
	signal.Notify(ch, os.Interrupt, syscall.SIGTERM)
	defer func() {
		signal.Stop(ch)
		cancel()
	}()
	go func() {
		select {
		case <-ch:
			cancel()
		case <-ctx.Done():
		}
	}()

	loadGenerator.run(ctx)
}