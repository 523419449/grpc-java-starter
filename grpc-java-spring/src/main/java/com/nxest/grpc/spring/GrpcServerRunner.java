/*
 *  Copyright (c) 2017, salesforce.com, inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.nxest.grpc.spring;

import com.google.common.base.Preconditions;
import com.nxest.grpc.spring.configure.GrpcServerBuilderConfigurer;
import com.nxest.grpc.spring.configure.GrpcServerProperties;
import com.nxest.grpc.spring.context.GrpcServerInitializedEvent;
import io.grpc.BindableService;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerServiceDefinition;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.netty.handler.ssl.ClientAuth;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContextBuilder;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslProvider;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import javax.annotation.Nonnull;
import java.io.File;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static java.lang.String.format;

/**
 * {@code GrpcServerRunner} configures a gRPC {@link Server} with services obtained from the {@link ApplicationContext}
 * and manages that server's lifecycle. Services are discovered by finding {@link BindableService} implementations that
 * are annotated with {@link GrpcService}.
 */
public class GrpcServerRunner implements AutoCloseable, ApplicationContextAware, DisposableBean {

    private static final Logger logger = Logger.getLogger(GrpcServerRunner.class.getName());

    private ApplicationContext applicationContext;

    private volatile Server server;

    private GrpcServerBuilderConfigurer serverBuilderConfigurer;

    private GrpcServerProperties grpcServerProperties;

    public GrpcServerRunner() {

    }

    public GrpcServerRunner(GrpcServerBuilderConfigurer serverBuilderConfigurer, GrpcServerProperties grpcServerProperties) {
        this.serverBuilderConfigurer = Preconditions.checkNotNull(serverBuilderConfigurer);
        this.grpcServerProperties = Preconditions.checkNotNull(grpcServerProperties);
    }


    public void setServerBuilderConfigurer(GrpcServerBuilderConfigurer serverBuilderConfigurer) {
        this.serverBuilderConfigurer = Preconditions.checkNotNull(serverBuilderConfigurer);
    }

    public void setGrpcServerProperties(GrpcServerProperties grpcServerProperties) {
        this.grpcServerProperties = Preconditions.checkNotNull(grpcServerProperties);
    }

    @Override
    public void setApplicationContext(@Nonnull ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = Preconditions.checkNotNull(applicationContext);
    }


    public void start() throws Exception {
        logger.info("Starting gRPC Server ...");

        initDefault();

        int port = grpcServerProperties.getPort();

        ServerBuilder serverBuilder = ServerBuilder.forPort(port);

        // find and register all GRpcService-enabled beans
        Collection<BindableService> services = getServicesWithAnnotation();

        for (BindableService srv : services) {
            ServerServiceDefinition serviceDefinition = srv.bindService();
            serverBuilder.addService(serviceDefinition);
            logger.info(srv.getClass().getName() + " service has been registered.");
        }

        //TODO:SSL/TLS supports

        serverBuilderConfigurer.configure(serverBuilder);
        server = serverBuilder.build();


        server.start();
        applicationContext.publishEvent(new GrpcServerInitializedEvent(server));
        logger.info("gRPC Server started, listening on port " + port);
        blockUntilShutdown();
    }

    private void initDefault() {
        if (Objects.isNull(this.serverBuilderConfigurer)) {
            this.serverBuilderConfigurer = new GrpcServerBuilderConfigurer();
        }
        if (Objects.isNull(this.grpcServerProperties)) {
            this.grpcServerProperties = new GrpcServerProperties();
        }
    }

    private SslContextBuilder getSslContextBuilder() {
        //        Init SSL/TLS
//        this.certChainFilePath = certChainFilePath;
//        this.privateKeyFilePath = privateKeyFilePath;
//        this.trustCertCollectionFilePath = trustCertCollectionFilePath;
//        System.out.println(
//            "USAGE: HelloWorldServerTls host port certChainFilePath privateKeyFilePath " +
//                "[trustCertCollectionFilePath]\n  Note: You only need to supply trustCertCollectionFilePath if you want " +
//                "to enable Mutual TLS.");
        String certChainFilePath = "certChainFilePath";
        String privateKeyFilePath = "privateKeyFilePath";
        String trustCertCollectionFilePath = "trustCertCollectionFilePath";
        SslContextBuilder sslClientContextBuilder = SslContextBuilder.forServer(new File(certChainFilePath),
            new File(privateKeyFilePath));
        if (trustCertCollectionFilePath != null) {
            sslClientContextBuilder.trustManager(new File(trustCertCollectionFilePath));
            sslClientContextBuilder.clientAuth(ClientAuth.REQUIRE);
        }
        return GrpcSslContexts.configure(sslClientContextBuilder, SslProvider.OPENSSL);
    }

    private void blockUntilShutdown() {
        Thread awaitThread = new Thread(() -> {
            try {
                GrpcServerRunner.this.server.awaitTermination();
            } catch (InterruptedException e) {
                logger.warning("gRPC server stopped." + e);
            }
        });
        awaitThread.setDaemon(false);
        awaitThread.start();
    }

    private Collection<BindableService> getServicesWithAnnotation() {

        Map<String, Object> possibleServices = applicationContext.getBeansWithAnnotation(GrpcService.class);

        Collection<String> invalidServiceNames = possibleServices.entrySet().stream()
            .filter(e -> !(e.getValue() instanceof BindableService))
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());

        if (!invalidServiceNames.isEmpty()) {
            throw new IllegalStateException((format(
                "The following beans are annotated with @GrpcService, but are not BindableServices: %s",
                String.join(", ", invalidServiceNames))));
        }

        return possibleServices.values().stream().map(s -> (BindableService) s).collect(Collectors.toList());
    }

    /**
     * Shutdown the gRPC {@link Server} when this object is closed.
     */
    @Override
    public void close() {
        final Server server = server();
        if (server != null) {
            // Use stderr here since the logger may have been reset by its JVM shutdown hook.

            System.err.println("Shutting down gRPC server ...");

            server.shutdown();

            try {
                server.awaitTermination(grpcServerProperties.getShutdownDelayMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                System.err.println("gRPC server stopped." + e);
            } finally {
                server.shutdownNow();
                this.server = null;
            }
            System.err.println("gRPC server stopped.");
        }
    }

    @Override
    public void destroy() throws Exception {
        this.close();
    }

    private Server server() {
        return server;
    }
}