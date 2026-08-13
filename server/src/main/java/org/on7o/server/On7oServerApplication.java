package org.on7o.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class On7oServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(On7oServerApplication.class, args);
    }
}
