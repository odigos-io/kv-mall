#include <httplib.h>
#include <nlohmann/json.hpp>
#include <opentelemetry/trace/provider.h>
#include <opentelemetry/trace/scope.h>
#include <opentelemetry/exporters/otlp/otlp_http_exporter.h>
#include <opentelemetry/sdk/trace/simple_processor.h>
#include <opentelemetry/sdk/trace/tracer_provider.h>
#include <opentelemetry/sdk/trace/samplers/always_on.h>
#include <opentelemetry/sdk/resource/resource.h>
#include <opentelemetry/sdk/trace/batch_span_processor.h>
#include <opentelemetry/context/propagation/global_propagator.h>
#include <opentelemetry/context/propagation/text_map_propagator.h>
#include <opentelemetry/trace/propagation/http_trace_context.h>
#include <iostream>
#include <memory>
#include <opentelemetry/common/macros.h>
#include <opentelemetry/nostd/shared_ptr.h>
#include <csignal>
#include <atomic>
#include <thread>

namespace trace = opentelemetry::trace;
namespace sdktrace = opentelemetry::sdk::trace;
namespace resource = opentelemetry::sdk::resource;
namespace otlp = opentelemetry::exporter::otlp;
namespace context = opentelemetry::context;
namespace propagation = opentelemetry::context::propagation;

std::atomic<bool> stop_server{false};

void signal_handler(int signal) {
    if (signal == SIGINT || signal == SIGTERM) {
        std::cout << "\nReceived signal, shutting down server..." << std::endl;
        stop_server = true;
    }
}

void InitTracer() {
    otlp::OtlpHttpExporterOptions opts;
    opts.url = "http://odigos-gateway.odigos-system:4318/v1/traces";
    
    // Create OTLP exporter
    auto exporter = std::make_unique<otlp::OtlpHttpExporter>(opts);
    
    // Use SimpleSpanProcessor for now since BatchSpanProcessor has issues
    auto processor = std::make_unique<sdktrace::SimpleSpanProcessor>(std::move(exporter));
    
    // Create a TracerProvider with the processor and AlwaysOn sampler
    auto provider = std::make_shared<sdktrace::TracerProvider>(
        std::move(processor),
        resource::Resource::Create({
            {"service.name", "recommendations"},
            {"service.version", "1.0.0"}
        }),
        std::make_unique<sdktrace::AlwaysOnSampler>()
    );
    
    // Set the global TracerProvider
    trace::Provider::SetTracerProvider(opentelemetry::nostd::shared_ptr<trace::TracerProvider>(provider));

    // Set the global propagator
    propagation::GlobalTextMapPropagator::SetGlobalPropagator(
        opentelemetry::nostd::shared_ptr<propagation::TextMapPropagator>(
            new trace::propagation::HttpTraceContext()
        )
    );

    std::cout << "Tracer initialized successfully" << std::endl;
}

// Helper class to extract context from HTTP headers
class HttpHeaderCarrier : public propagation::TextMapCarrier {
public:
    HttpHeaderCarrier(const httplib::Headers& headers) : headers_(headers) {}

    opentelemetry::nostd::string_view Get(opentelemetry::nostd::string_view key) const noexcept override {
        auto it = headers_.find(std::string(key));
        if (it != headers_.end()) {
            return it->second;
        }
        return "";
    }

    void Set(opentelemetry::nostd::string_view key, opentelemetry::nostd::string_view value) noexcept override {
        // Not needed for extraction
    }

private:
    const httplib::Headers& headers_;
};

int main() {
    InitTracer();
    auto tracer = trace::Provider::GetTracerProvider()->GetTracer("github.com/odigos-io/ebpf-cpp-instrumentation/httplib");
    httplib::Server svr;

    svr.Get("/recommendations", [tracer](const httplib::Request& req, httplib::Response& res) {
        // Extract context from headers
        HttpHeaderCarrier carrier(req.headers);
        auto current_ctx = context::RuntimeContext::GetCurrent();
        auto extracted_ctx = propagation::GlobalTextMapPropagator::GetGlobalPropagator()->Extract(
            carrier, current_ctx
        );

        // Create span with extracted context
        trace::StartSpanOptions options;
        options.parent = extracted_ctx;
        options.kind = trace::SpanKind::kServer;
        
        auto span = tracer->StartSpan(
            "GET /recommendations",
            {
                {"http.method", "GET"},
                {"http.url", "/recommendations"},
            },
            options
        );
        
        opentelemetry::trace::Scope scope(span);
        
        std::string product_id = req.get_param_value("product_id");
        
        nlohmann::json response = {
            {"product_id", product_id},
            {"recommendations", {"prod-101", "prod-102", "prod-103"}}
        };
        res.set_content(response.dump(), "application/json");
        span->End();
    });

    std::signal(SIGINT, signal_handler);
    std::signal(SIGTERM, signal_handler);
    std::cout << "Recommendations service running on http://0.0.0.0:8081" << std::endl;
    // Start the server in a separate thread
    std::thread server_thread([&svr]() {
        svr.listen("0.0.0.0", 8081);
    });
    // Wait for stop signal
    while (!stop_server) {
        std::this_thread::sleep_for(std::chrono::milliseconds(100));
    }
    svr.stop();
    // Give the server thread up to 2 seconds to join
    if (server_thread.joinable()) {
        for (int i = 0; i < 20; ++i) {
            if (server_thread.joinable()) {
                std::this_thread::sleep_for(std::chrono::milliseconds(100));
            } else {
                break;
            }
        }
        server_thread.join();
    }
    std::cout << "Server stopped." << std::endl;
    return 0;
} 