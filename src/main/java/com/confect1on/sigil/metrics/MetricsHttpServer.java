package com.confect1on.sigil.metrics;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * Simple HTTP server that exposes metrics in Prometheus format.
 */
public class MetricsHttpServer {
    private final MetricsExporter metricsExporter;
    private final int port;
    private HttpServer server;

    public MetricsHttpServer(MetricsExporter metricsExporter, int port) {
        this.metricsExporter = metricsExporter;
        this.port = port;
    }

    public void start() {
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/metrics", httpExchange -> {
                String response = metricsExporter.exportMetrics();
                httpExchange.getResponseHeaders().set("Content-Type", "text/plain; version=0.0.4");
                httpExchange.sendResponseHeaders(200, response.length());
                try (OutputStream os = httpExchange.getResponseBody()) {
                    os.write(response.getBytes(StandardCharsets.UTF_8));
                }
            });
            server.setExecutor(null); // Use the default executor
            server.start();
        } catch (IOException e) {
            throw new RuntimeException("Failed to start metrics server", e);
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }
}
