package com.nxest.grpc.spring.test.config;

import com.nxest.grpc.spring.configure.GrpcServerProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties("grpc.spring")
public class GrpcProperties {

    /**
     * Grpc Server Properties
     */
    private final GrpcServerProperties server = new GrpcServerProperties();

    public GrpcServerProperties getServer() {
        return server;
    }
}
