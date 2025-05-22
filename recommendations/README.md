# Recommendations Service (C++)

This is a dummy HTTP microservice for e-commerce product recommendations, written in C++ and instrumented with OpenTelemetry.

## API

- `GET /recommendations?product_id=123`  
  Returns a JSON array of recommended product IDs for the given product.

## Build

```sh
mkdir build
cd build
cmake ..
make
```

## Run

```sh
./recommendations
```

The service will listen on port 8081.

## OpenTelemetry

This service is manually instrumented with OpenTelemetry C++ SDK. Spans are exported to stdout by default.

## Docker

Build the Docker image:

```sh
docker build -t recommendations .
```

## Kubernetes

See the deployment YAMLs in the `../deployment` directory. 