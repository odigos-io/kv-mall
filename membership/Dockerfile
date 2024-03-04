FROM golang:1.21 as builder
WORKDIR /go/src/app
COPY . .
RUN CGO_ENABLED=0 go build -o /go/bin/membership

FROM gcr.io/distroless/base-debian10:debug
COPY --from=builder /go/bin/membership /
USER 15000
ENTRYPOINT ["/membership"]