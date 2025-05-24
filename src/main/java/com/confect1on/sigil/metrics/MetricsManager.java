package com.confect1on.sigil.metrics;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Manages metrics collection and storage for Sigil.
 * Thread-safe implementation for concurrent access.
 */
public class MetricsManager {
    private static final double[] DURATION_HISTOGRAM_BUCKETS = {
        5,      // 5 seconds
        15,     // 15 seconds
        30,     // 30 seconds
        60,     // 1 minute
        120,    // 2 minutes
        300,    // 5 minutes
        600,    // 10 minutes
        900,    // 15 minutes
        1800,   // 30 minutes
        3600,   // 1 hour
        7200,   // 2 hours
        10800,  // 3 hours
        14400,  // 4 hours
        18000,  // 5 hours
        21600,  // 6 hours
        25200,  // 7 hours
        28800,  // 8 hours
        32400,  // 9 hours
        36000,  // 10 hours
        39600,  // 11 hours
        43200,  // 12 hours
        Double.POSITIVE_INFINITY
    };
   
    private final Map<UUID, PlayerSession> activeSessions = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> playersByRegion = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> playersByBackend = new ConcurrentHashMap<>();
    
    // Session Duration Histogram
    private final NavigableMap<Double, AtomicInteger> sessionDurationHistogramBuckets = new TreeMap<>();
    private final AtomicLong sessionDurationSum = new AtomicLong(0);
    private final AtomicInteger sessionDurationCount = new AtomicInteger(0);

    private final Map<String, Boolean> backendStatus = new ConcurrentHashMap<>();
    private final Map<String, Long> lastPingTime = new ConcurrentHashMap<>();
    private static final long PING_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(30);

    public MetricsManager() {
        // Initialize duration histogram buckets
        for (double bucket : DURATION_HISTOGRAM_BUCKETS) {
            sessionDurationHistogramBuckets.put(bucket, new AtomicInteger(0));
        }
        // Prometheus also expects an +Inf bucket, often represented by the _count metric itself
        // or explicitly if library requires it. We will handle it in exporter.
    }

    public void playerConnected(Player player, String region) {
        UUID playerId = player.getUniqueId();
        
        // Remove player from old region if they were already connected
        PlayerSession oldSession = activeSessions.get(playerId);
        if (oldSession != null) {
            decrementRegionCount(oldSession.region());
            decrementBackendCount(oldSession.currentServer());
        }
        
        // Add to new region
        activeSessions.put(playerId, new PlayerSession(player, region));
        incrementRegionCount(region);
    }

    public void playerDisconnected(UUID playerId) {
        PlayerSession session = activeSessions.remove(playerId);
        if (session != null) {
            decrementRegionCount(session.region());
            decrementBackendCount(session.currentServer());
            
            // Update session duration metrics
            Duration duration = Duration.between(session.connectTime(), Instant.now());
            long seconds = duration.getSeconds();
            sessionDurationSum.addAndGet(seconds);
            sessionDurationCount.incrementAndGet();
            
            // Increment all relevant buckets (cumulative)
            for (double bucket : sessionDurationHistogramBuckets.keySet()) { // Iterate sorted keys
                if (seconds <= bucket) {
                    sessionDurationHistogramBuckets.get(bucket).incrementAndGet();
                }
            }
        }
    }

    public void updatePlayerServer(UUID playerId, RegisteredServer server) {
        PlayerSession session = activeSessions.get(playerId);
        if (session != null) {
            String oldServer = session.currentServer();
            if (oldServer != null) {
                decrementBackendCount(oldServer);
            }
            session.updateServer(server.getServerInfo().getName());
            incrementBackendCount(server.getServerInfo().getName());
        }
    }

    private void incrementRegionCount(String region) {
        playersByRegion.computeIfAbsent(region, k -> new AtomicInteger(0))
                      .incrementAndGet();
    }

    private void decrementRegionCount(String region) {
        AtomicInteger count = playersByRegion.get(region);
        if (count != null && count.decrementAndGet() <= 0) {
            playersByRegion.remove(region);
        }
    }

    private void incrementBackendCount(String server) {
        if (server != null) {
            playersByBackend.computeIfAbsent(server, k -> new AtomicInteger(0))
                          .incrementAndGet();
        }
    }

    private void decrementBackendCount(String server) {
        if (server != null) {
            AtomicInteger count = playersByBackend.get(server);
            if (count != null && count.decrementAndGet() <= 0) {
                playersByBackend.remove(server);
            }
        }
    }

    // Metric accessors
    public int getActiveSessionCount() {
        return activeSessions.size();
    }

    public Map<String, Integer> getPlayersByRegion() {
        Map<String, Integer> result = new ConcurrentHashMap<>();
        playersByRegion.forEach((region, count) -> {
            int value = count.get();
            if (value > 0) {
                result.put(region, value);
            }
        });
        return result;
    }

    public Map<String, Integer> getPlayersByBackend() {
        Map<String, Integer> result = new ConcurrentHashMap<>();
        playersByBackend.forEach((server, count) -> {
            int value = count.get();
            if (value > 0) {
                result.put(server, value);
            }
        });
        return result;
    }

    // Getters for Session Duration Histogram
    public NavigableMap<Double, Integer> getSessionDurationHistogramBuckets() {
        NavigableMap<Double, Integer> result = new TreeMap<>();
        sessionDurationHistogramBuckets.forEach((bucket, count) -> result.put(bucket, count.get()));
        return result;
    }

    public long getSessionDurationSum() {
        return sessionDurationSum.get();
    }

    public int getSessionDurationCount() {
        return sessionDurationCount.get();
    }

    /**
     * Update backend server status based on ping result
     */
    public void updateBackendStatus(String serverName, boolean isOnline) {
        backendStatus.put(serverName, isOnline);
        lastPingTime.put(serverName, System.currentTimeMillis());
    }

    /**
     * Get current backend server status
     * @return Map of server names to their status (true = up, false = down)
     */
    public Map<String, Boolean> getBackendStatus() {
        long now = System.currentTimeMillis();
        Map<String, Boolean> current = new ConcurrentHashMap<>();
        
        backendStatus.forEach((server, status) -> {
            // Consider server down if we haven't received a ping in PING_TIMEOUT_MS
            Long lastPing = lastPingTime.get(server);
            boolean isUp = status && lastPing != null && 
                         (now - lastPing) <= PING_TIMEOUT_MS;
            current.put(server, isUp);
        });
        
        return current;
    }

    private static class PlayerSession {
        private final Player player;
        private final String region;
        private final Instant connectTime;
        private String currentServer;

        PlayerSession(Player player, String region) {
            this.player = player;
            this.region = region;
            this.connectTime = Instant.now();
        }

        String region() { return region; }
        Instant connectTime() { return connectTime; }
        String currentServer() { return currentServer; }
        
        void updateServer(String server) {
            this.currentServer = server;
        }
    }
}
