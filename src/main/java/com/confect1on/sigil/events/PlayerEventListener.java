package com.confect1on.sigil.events;

import com.confect1on.sigil.geo.GeoIpService;
import com.confect1on.sigil.metrics.MetricsManager;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.Player;
import org.slf4j.Logger;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import com.velocitypowered.api.proxy.ProxyServer;

/**
 * Listens for Velocity player events and updates metrics accordingly.
 */
public class PlayerEventListener {
    private final MetricsManager metricsManager;
    private final GeoIpService geoIpService;
    private final Logger logger;

    public PlayerEventListener(MetricsManager metricsManager, GeoIpService geoIpService, Logger logger, ProxyServer proxy) {
        this.metricsManager = metricsManager;
        this.geoIpService = geoIpService;
        this.logger = logger;
    }

    public void startLatencyUpdates() {
        // No longer needed
    }

    @Subscribe
    public void onPlayerJoin(PostLoginEvent event) {
        Player player = event.getPlayer();
        InetSocketAddress socketAddress = player.getRemoteAddress();
        
        if (socketAddress == null) {
            logger.warn("Player {} has null remote address!", player.getUsername());
            return;
        }

        InetAddress playerIp = socketAddress.getAddress();
        if (playerIp == null) {
            logger.warn("Could not get IP address for player {}", player.getUsername());
            return;
        }

        logger.info("Player {} connecting from IP: {}", player.getUsername(), playerIp);
        
        String region = geoIpService.getCountryCode(playerIp)
                                  .orElse("UNKNOWN");
        
        logger.info("Resolved region for {} ({}): {}", player.getUsername(), playerIp, region);
        
        metricsManager.playerConnected(event.getPlayer(), region);
    }

    @Subscribe
    public void onPlayerDisconnect(DisconnectEvent event) {
        Player player = event.getPlayer();
        logger.debug("Player {} disconnected from {}", 
            player.getUsername(), 
            player.getRemoteAddress().getAddress());
            
        metricsManager.playerDisconnected(event.getPlayer().getUniqueId());
    }

    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        Player player = event.getPlayer();
        String server = event.getServer().getServerInfo().getName();
        logger.info("Player {} connected to backend server {}", player.getUsername(), server);
        metricsManager.updatePlayerServer(player.getUniqueId(), event.getServer());
    }
}
