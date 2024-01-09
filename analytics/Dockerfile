FROM golang:1.21 as builder
WORKDIR /go/src/app
COPY . .
RUN go build -o /go/bin/analytics main.go

FROM gcr.io/distroless/base-debian12
COPY --from=builder /go/bin/analytics /
ENTRYPOINT ["/analytics"]