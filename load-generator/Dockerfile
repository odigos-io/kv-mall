FROM golang:1.21.2 as builder
WORKDIR /go/src/app
COPY . .
RUN go build -o /go/bin/load-generator main.go

FROM gcr.io/distroless/base-debian12
COPY --from=builder /go/bin/load-generator /
USER 15000
ENTRYPOINT ["/load-generator"]