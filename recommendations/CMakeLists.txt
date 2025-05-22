cmake_minimum_required(VERSION 3.10)
project(recommendations)

set(CMAKE_CXX_STANDARD 17)

# Find OpenTelemetry
find_package(opentelemetry-cpp CONFIG REQUIRED)

# Add executable
add_executable(recommendations main.cpp)

# Include cpp-httplib (header-only)
target_include_directories(recommendations PRIVATE ${CMAKE_SOURCE_DIR}/external)

# Link OpenTelemetry
find_package(Threads REQUIRED)
target_link_libraries(recommendations PRIVATE 
    opentelemetry_trace 
    opentelemetry_exporter_otlp_http 
    opentelemetry_exporter_otlp_http_client
    opentelemetry_otlp_recordable
    opentelemetry_resources 
    opentelemetry_common 
    Threads::Threads
) 