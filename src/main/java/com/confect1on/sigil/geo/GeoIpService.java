package com.confect1on.sigil.geo;

import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.GeoIp2Exception;
import com.maxmind.geoip2.model.CountryResponse;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.net.InetAddress;
import java.util.Optional;
import java.util.Random;

/**
 * GeoIpService resolves IP addresses to ISO country codes using MaxMind GeoLite2.
 * Falls back to simple heuristics if the database is unavailable.
 *
 * This product includes GeoLite2 data created by MaxMind, available from https://www.maxmind.com.
 * Licensed under CC BY-SA 4.0.
 */
public class GeoIpService {
    private static final String DB_RESOURCE = "GeoLite2-Country.mmdb";
    private static final boolean DEV_MODE = true; // Set to false in production
    private static final String[] TEST_REGIONS = {"US", "GB", "DE", "FR", "JP", "BR", "AU"};
    
    private final Logger logger;
    private final Random random;
    private DatabaseReader dbReader;
    private boolean dbAvailable = false;

    public GeoIpService(Logger logger, Path dataDirectory) {
        this.logger = logger;
        this.random = new Random();
        initializeDatabase(dataDirectory);
    }

    private void initializeDatabase(Path dataDirectory) {
        try {
            // Log the resource URL to verify it exists
            logger.info("Looking for GeoIP database in resources: {}", 
                getClass().getClassLoader().getResource(DB_RESOURCE));

            // Ensure data directory exists
            Files.createDirectories(dataDirectory);
            Path dbPath = dataDirectory.resolve(DB_RESOURCE);
            logger.info("Database path: {}", dbPath.toAbsolutePath());

            // Copy database from resources if it doesn't exist
            if (!Files.exists(dbPath)) {
                try (InputStream is = getClass().getClassLoader().getResourceAsStream(DB_RESOURCE)) {
                    if (is == null) {
                        logger.error("GeoLite2-Country.mmdb not found in plugin resources!");
                        return;
                    }
                    Files.copy(is, dbPath, StandardCopyOption.REPLACE_EXISTING);
                    logger.info("Copied GeoLite2-Country.mmdb to: {}", dbPath.toAbsolutePath());
                }
            }

            // Load the database
            if (Files.exists(dbPath)) {
                dbReader = new DatabaseReader.Builder(dbPath.toFile()).build();
                dbAvailable = true;
                logger.info("Successfully loaded GeoLite2-Country.mmdb");
                
                // Test the database with a known IP
                try {
                    InetAddress testIp = InetAddress.getByName("8.8.8.8"); // Google DNS
                    CountryResponse response = dbReader.country(testIp);
                    logger.info("Database test successful - resolved 8.8.8.8 to: {}", 
                        response.getCountry().getIsoCode());
                } catch (Exception e) {
                    logger.error("Database test failed: {}", e.getMessage());
                }
            }
        } catch (IOException e) {
            logger.error("Failed to initialize GeoIP database: {}", e.getMessage(), e);
        }
        
        if (!dbAvailable) {
            logger.warn("GeoIP database not available. Using fallback detection.");
        }

        if (DEV_MODE) {
            logger.info("Running in DEV mode - localhost connections will be assigned random regions");
        }
    }

    /**
     * Resolves an IP address to a country code (ISO 3166-1 alpha-2).
     * @param ip The IP address to resolve
     * @return Optional country code, or empty if unknown
     */
    public Optional<String> getCountryCode(InetAddress ip) {
        // In dev mode, assign random regions to localhost connections
        if (DEV_MODE && (ip.isLoopbackAddress() || ip.isSiteLocalAddress())) {
            String region = TEST_REGIONS[random.nextInt(TEST_REGIONS.length)];
            logger.info("DEV MODE: Assigning random region {} to local address {}", region, ip);
            return Optional.of(region);
        }

        if (dbAvailable) {
            try {
                CountryResponse response = dbReader.country(ip);
                String countryCode = response.getCountry().getIsoCode();
                logger.debug("Resolved {} to country code: {}", ip, countryCode);
                return Optional.ofNullable(countryCode);
            } catch (IOException | GeoIp2Exception e) {
                logger.debug("GeoIP lookup failed for {}: {}", ip, e.getMessage());
            }
        }

        // Standard fallback for non-local addresses
        if (ip.isLoopbackAddress() || ip.isSiteLocalAddress()) {
            logger.debug("{} is a local address", ip);
            return Optional.of("LOCAL");
        }

        logger.debug("{} could not be resolved, marking as UNKNOWN", ip);
        return Optional.of("UNKNOWN");
    }
} 