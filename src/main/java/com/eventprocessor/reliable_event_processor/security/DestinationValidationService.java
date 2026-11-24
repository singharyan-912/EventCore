package com.eventprocessor.reliable_event_processor.security;

import com.eventprocessor.reliable_event_processor.exception.SsrfProtectionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.URI;
import java.util.Set;

/**
 * Validates outbound integration and webhook destinations against SSRF, DNS rebinding,
 * cloud metadata access, and private network probing.
 */
@Service
public class DestinationValidationService {

    private static final Logger log = LoggerFactory.getLogger(DestinationValidationService.class);

    private static final Set<String> BLOCKED_HOSTNAMES = Set.of(
            "localhost",
            "metadata.google.internal",
            "instance-data",
            "metadata"
    );

    private static final String AWS_GCP_METADATA_IP = "169.254.169.254";

    private final boolean allowInsecureHttp;
    private final boolean allowPrivateIps;

    public DestinationValidationService(
            @Value("${event-processor.integration.allow-insecure-http:false}") boolean allowInsecureHttp,
            @Value("${event-processor.integration.allow-private-ips:false}") boolean allowPrivateIps) {
        this.allowInsecureHttp = allowInsecureHttp;
        this.allowPrivateIps = allowPrivateIps;
    }

    public URI validateDestinationUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new SsrfProtectionException("Destination URL must not be blank");
        }

        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (Exception e) {
            throw new SsrfProtectionException("Malformed destination URL: " + e.getMessage());
        }

        String scheme = uri.getScheme();
        if (scheme == null) {
            throw new SsrfProtectionException("URL scheme is required");
        }
        scheme = scheme.toLowerCase();

        if ("http".equals(scheme)) {
            if (!allowInsecureHttp) {
                throw new SsrfProtectionException("Insecure HTTP protocol is disabled; HTTPS is required");
            }
        } else if (!"https".equals(scheme)) {
            throw new SsrfProtectionException("Unsupported protocol scheme: " + scheme + ". Only HTTPS (or test HTTP) is permitted");
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new SsrfProtectionException("Destination host must not be empty");
        }

        String normalizedHost = host.toLowerCase();
        if (BLOCKED_HOSTNAMES.contains(normalizedHost)) {
            if (!allowPrivateIps) {
                throw new SsrfProtectionException("Access to reserved host is blocked by SSRF policy: " + host);
            }
        }

        // DNS resolution check to verify resolved IP address
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            for (InetAddress address : addresses) {
                validateInetAddress(address, host);
            }
        } catch (SsrfProtectionException e) {
            throw e;
        } catch (Exception e) {
            throw new SsrfProtectionException("Failed to resolve destination host: " + host + " (" + e.getMessage() + ")");
        }

        int port = uri.getPort();
        if (port != -1 && port != 80 && port != 443) {
            if (!allowPrivateIps && (port < 1024 || port == 22 || port == 23 || port == 3306 || port == 5432 || port == 6379 || port == 27017)) {
                throw new SsrfProtectionException("Destination port " + port + " is disallowed by security policy");
            }
        }

        return uri;
    }

    private void validateInetAddress(InetAddress address, String originalHost) {
        String ip = address.getHostAddress();

        if (AWS_GCP_METADATA_IP.equals(ip)) {
            throw new SsrfProtectionException("Access to cloud metadata IP (169.254.169.254) is strictly forbidden");
        }

        if (allowPrivateIps) {
            return; // In local test mode with WireMock / mock HTTP server, local/private addresses are allowed
        }

        if (address.isLoopbackAddress()) {
            throw new SsrfProtectionException("Access to loopback IP (" + ip + ") is blocked by SSRF policy for host: " + originalHost);
        }

        if (address.isLinkLocalAddress()) {
            throw new SsrfProtectionException("Access to link-local IP (" + ip + ") is blocked by SSRF policy for host: " + originalHost);
        }

        if (address.isSiteLocalAddress()) {
            throw new SsrfProtectionException("Access to private network IP (" + ip + ") is blocked by SSRF policy for host: " + originalHost);
        }

        if (address.isAnyLocalAddress()) {
            throw new SsrfProtectionException("Access to wildcard local IP (" + ip + ") is blocked by SSRF policy");
        }
    }
}
