# syntax=docker/dockerfile:1
FROM ubuntu:22.04 AS build

RUN apt-get update && \
    apt-get install -y g++ cmake libcurl4-openssl-dev libssl-dev git wget \
    build-essential pkg-config protobuf-compiler libprotobuf-dev nlohmann-json3-dev && \
    rm -rf /var/lib/apt/lists/*

# OpenTelemetry C++ SDK
WORKDIR /opt
RUN git clone --depth 1 --branch v1.11.0 https://github.com/open-telemetry/opentelemetry-cpp.git
WORKDIR /opt/opentelemetry-cpp
RUN mkdir build && cd build && \
    cmake .. -DBUILD_TESTING=OFF -DWITH_OTLP_HTTP=ON -DWITH_OTLP_GRPC=OFF \
    -DBUILD_SHARED_LIBS=ON -DCMAKE_POSITION_INDEPENDENT_CODE=ON \
    -DWITH_EXAMPLES=OFF -DWITH_TEST=OFF -DWITH_BENCHMARK=OFF \
    -DWITH_ABSEIL=OFF -DWITH_PROMETHEUS=OFF -DWITH_ZIPKIN=OFF \
    -DWITH_JAEGER=OFF -DWITH_ELASTICSEARCH=OFF -DWITH_EXPERIMENTAL=OFF \
    -DWITH_EXPORTER_OTLP_HTTP=ON -DWITH_EXPORTER_OSTREAM=ON \
    -DWITH_LOGS_PREVIEW=ON -DCMAKE_INSTALL_LIBDIR=lib && \
    make -j$(nproc) && make install

# Download and install cpp-httplib
WORKDIR /opt
RUN git clone --depth 1 https://github.com/yhirose/cpp-httplib.git
RUN mkdir -p /usr/local/include/httplib
RUN cp cpp-httplib/httplib.h /usr/local/include/httplib/

# Build our app
WORKDIR /app
COPY . .
RUN rm -rf build && mkdir build && cd build && \
    cmake .. -DCMAKE_BUILD_TYPE=Release && \
    make -j$(nproc)

FROM ubuntu:22.04
RUN apt-get update && \
    apt-get install -y libcurl4-openssl-dev libssl-dev ca-certificates \
    libprotobuf-dev nlohmann-json3-dev && \
    rm -rf /var/lib/apt/lists/*

COPY --from=build /usr/local/lib/libopentelemetry* /usr/local/lib/
COPY --from=build /usr/local/include/opentelemetry /usr/local/include/opentelemetry
COPY --from=build /usr/local/include/httplib /usr/local/include/httplib
COPY --from=build /app/build/recommendations /usr/local/bin/recommendations

RUN ldconfig

EXPOSE 8081
CMD ["/usr/local/bin/recommendations"] 