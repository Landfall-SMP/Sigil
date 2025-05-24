# Sigil

Sigil is a telemetry plugin for Velocity proxy servers that tracks player activity and exposes metrics in Prometheus format. It provides real-time insights into your Minecraft network's performance and player demographics.

## Features

- Real-time player tracking and metrics
- Geographic location tracking using GeoLite2 database
- Backend server status monitoring
- Prometheus-compatible metrics endpoint
- Session duration tracking
- Player count by region and backend server

## Metrics Available

- Active player sessions
- Player distribution by region
- Player distribution by backend server
- Session duration histograms
- Backend server status

## Installation

1. Download the latest release
2. Place the JAR file in your Velocity server's `plugins` directory
3. Restart your Velocity server

## Metrics Access

Metrics are exposed in Prometheus format at:
```
http://localhost:9091/metrics
```

## Requirements

- Velocity 3.1.1 or higher
- Java 17 or higher

## License

This project is licensed under the MIT License.

### Third-party Licenses

GeoLite2 database is distributed under the Creative Commons Attribution-ShareAlike 4.0 International License.
