package com.confect1on.sigil;

import com.confect1on.sigil.events.PlayerEventListener;
import com.confect1on.sigil.geo.GeoIpService;
import com.confect1on.sigil.metrics.MetricsExporter;
import com.confect1on.sigil.metrics.MetricsHttpServer;
import com.confect1on.sigil.metrics.MetricsManager;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.Logger;

import com.google.inject.Inject;
import java.nio.file.Path;

@Plugin(
    id = "sigil",
    name = "Sigil",
    version = "1.0",
    description = "Telemetry for Velocity - Tracks player activity and exposes metrics to Prometheus"
)
public class Sigil {
    private final Logger logger;
    private final ProxyServer proxy;
    private final Path dataDirectory;
    private final MetricsManager metricsManager;
    private final GeoIpService geoIpService;
    private final MetricsExporter metricsExporter;

    @Inject
    public Sigil(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
        this.metricsManager = new MetricsManager();
        this.geoIpService = new GeoIpService(logger, dataDirectory);
        this.metricsExporter = new MetricsExporter(metricsManager);
        
        logger.info("Sigil initializing. This product includes GeoLite2 data created by MaxMind, available from https://www.maxmind.com");
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        // Create event listener
        PlayerEventListener listener = new PlayerEventListener(metricsManager, geoIpService, logger, proxy);
        
        // Register event listener
        proxy.getEventManager().register(this, listener);

        // Start metrics HTTP server
        new MetricsHttpServer(metricsExporter, 9091).start();
        
        // Start latency updates after plugin is registered
        listener.startLatencyUpdates();
        
        logger.info("Sigil initialized - tracking player metrics and geo-location data");
        logger.info("Metrics available at http://localhost:9091/metrics");
    }

    public MetricsManager getMetricsManager() {
        return metricsManager;
    }
}
