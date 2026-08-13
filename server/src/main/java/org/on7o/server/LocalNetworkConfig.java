package org.on7o.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collections;
import java.util.List;

/**
 * Local-network posture for the current milestone: no authentication, no TLS, and
 * CORS wide open so a laptop or phone on the same Wi-Fi can talk to the server
 * while the capture loop is being proven out.
 *
 * <p>This is deliberate and temporary. Do not expose this port beyond the LAN.
 */
@Configuration
public class LocalNetworkConfig implements WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(LocalNetworkConfig.class);

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
                .allowedMethods("*");
    }

    /** Prints the ingest URL to paste into the firmware configuration. */
    @Bean
    ApplicationListener<ApplicationReadyEvent> ingestUrlBanner() {
        return event -> {
            int port = ((WebServerApplicationContext) event.getApplicationContext())
                    .getWebServer().getPort();
            log.warn("on7o server is UNAUTHENTICATED and intended for LAN use only");
            for (String address : localAddresses()) {
                log.info("ingest endpoint: http://{}:{}/api/thoughts/audio", address, port);
            }
        };
    }

    private static List<String> localAddresses() {
        try {
            return Collections.list(NetworkInterface.getNetworkInterfaces()).stream()
                    .filter(LocalNetworkConfig::isUsable)
                    .flatMap(nic -> Collections.list(nic.getInetAddresses()).stream())
                    .filter(Inet4Address.class::isInstance)
                    .map(java.net.InetAddress::getHostAddress)
                    .toList();
        } catch (SocketException e) {
            log.debug("could not enumerate network interfaces", e);
            return List.of("localhost");
        }
    }

    private static boolean isUsable(NetworkInterface nic) {
        try {
            return nic.isUp() && !nic.isLoopback();
        } catch (SocketException e) {
            return false;
        }
    }
}
