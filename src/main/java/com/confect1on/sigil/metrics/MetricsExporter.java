package com.confect1on.sigil.metrics;

import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;

/**
 * Formats metrics in Prometheus text format.
 * Follows best practices:
 * - Only shows metrics with meaningful values
 * - No empty/zero gauges
 * - Proper help and type annotations
 */
public class MetricsExporter {
    private final MetricsManager metricsManager;

    public MetricsExporter(MetricsManager metricsManager) {
        this.metricsManager = metricsManager;
    }

    /**
     * Generates all metrics in Prometheus text format.
     * @return Formatted metrics string
     */
    public String exportMetrics() {
        StringBuilder sb = new StringBuilder();

        // Backend server status - always show all known backends
        Map<String, Boolean> backendStatus = metricsManager.getBackendStatus();
        if (!backendStatus.isEmpty()) {
            sb.append("# HELP sigil_backend_status Backend server status (1 = up, 0 = down)\n");
            sb.append("# TYPE sigil_backend_status gauge\n");
            backendStatus.forEach((server, isUp) -> 
                sb.append(String.format("sigil_backend_status{server=\"%s\"} %d\n",
                        server, isUp ? 1 : 0))
            );
        }

        // Active sessions gauge - only show if players are online
        int activeSessions = metricsManager.getActiveSessionCount();
        if (activeSessions > 0) {
            if (sb.length() > 0) sb.append("\n");
            sb.append("# HELP sigil_active_sessions Number of players currently connected\n");
            sb.append("# TYPE sigil_active_sessions gauge\n");
            sb.append("sigil_active_sessions ").append(activeSessions).append("\n");
        }

        // Players by region gauge - only output regions that have active players
        Map<String, Integer> regionCounts = metricsManager.getPlayersByRegion();
        boolean hasRegions = regionCounts.values().stream().anyMatch(count -> count > 0);
        if (hasRegions) {
            if (sb.length() > 0) sb.append("\n");
            sb.append("# HELP sigil_players_by_region Number of active players by region\n");
            sb.append("# TYPE sigil_players_by_region gauge\n");
            regionCounts.forEach((region, count) -> {
                if (count > 0) {
                    sb.append(String.format("sigil_players_by_region{region=\"%s\"} %d\n",
                            region, count));
                }
            });
        }

        // Players by backend server - only show active servers
        Map<String, Integer> backendCounts = metricsManager.getPlayersByBackend();
        boolean hasBackendPlayers = backendCounts.values().stream().anyMatch(count -> count > 0);
        if (hasBackendPlayers) {
            if (sb.length() > 0) sb.append("\n");
            sb.append("# HELP sigil_backend_players Number of players per backend server\n");
            sb.append("# TYPE sigil_backend_players gauge\n");
            backendCounts.forEach((server, count) -> {
                if (count > 0) {
                    sb.append(String.format("sigil_backend_players{server=\"%s\"} %d\n",
                            server, count));
                }
            });
        }

        // Session Duration Histogram
        int completedSessionCount = metricsManager.getSessionDurationCount();
        if (completedSessionCount > 0) {
            if (sb.length() > 0) sb.append("\n");
            sb.append("# HELP sigil_session_duration_seconds Duration of completed player sessions in seconds\n");
            sb.append("# TYPE sigil_session_duration_seconds histogram\n");
            
            NavigableMap<Double, Integer> histogramBuckets = metricsManager.getSessionDurationHistogramBuckets();
            for (Map.Entry<Double, Integer> entry : histogramBuckets.entrySet()) {
                String le = entry.getKey().isInfinite() ? "+Inf" : String.format(Locale.US, "%.0f", entry.getKey());
                sb.append(String.format(Locale.US, "sigil_session_duration_seconds_bucket{le=\"%s\"} %d\n", le, entry.getValue()));
            }
            
            sb.append(String.format(Locale.US, "sigil_session_duration_seconds_sum %.1f\n", (double) metricsManager.getSessionDurationSum()));
            sb.append(String.format(Locale.US, "sigil_session_duration_seconds_count %d\n", completedSessionCount));
        }

        return sb.toString();
    }
} 