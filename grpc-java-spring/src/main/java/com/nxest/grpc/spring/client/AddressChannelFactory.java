package com.nxest.grpc.spring.client;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.nxest.grpc.spring.client.configure.GrpcChannelProperties;
import io.grpc.*;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class AddressChannelFactory implements GrpcChannelFactory {
    private final GrpcChannelProperties properties;
    private final LoadBalancer.Factory loadBalancerFactory;
    private final NameResolver.Factory nameResolverFactory;
    private final GlobalClientInterceptorRegistry globalClientInterceptorRegistry;

    public AddressChannelFactory(GrpcChannelProperties properties, LoadBalancer.Factory loadBalancerFactory, GlobalClientInterceptorRegistry globalClientInterceptorRegistry) {
        this.properties = properties;
        this.loadBalancerFactory = loadBalancerFactory;
        this.nameResolverFactory = new AddressChannelResolverFactory(properties);
        this.globalClientInterceptorRegistry = globalClientInterceptorRegistry;
    }

    @Override
    public Channel createChannel(String name) {
        return this.createChannel(name, null);
    }

    @Override
    public Channel createChannel(String name, List<ClientInterceptor> interceptors) {
        GrpcChannelProperties channelProperties = properties;
        NettyChannelBuilder builder = NettyChannelBuilder.forTarget(name)
            .loadBalancerFactory(loadBalancerFactory)
            .nameResolverFactory(nameResolverFactory);
        builder.negotiationType(channelProperties.getNegotiationType());
        if (channelProperties.isEnableKeepAlive()) {
            builder.keepAliveWithoutCalls(channelProperties.isKeepAliveWithoutCalls())
                .keepAliveTime(channelProperties.getKeepAliveTime(), TimeUnit.SECONDS)
                .keepAliveTimeout(channelProperties.getKeepAliveTimeout(), TimeUnit.SECONDS);
        }
        if (channelProperties.getMaxInboundMessageSize() > 0) {
            builder.maxInboundMessageSize(channelProperties.getMaxInboundMessageSize());
        }
        if (channelProperties.isFullStreamDecompression()) {
            builder.enableFullStreamDecompression();
        }
        Channel channel = builder.build();

        List<ClientInterceptor> globalInterceptorList = globalClientInterceptorRegistry.getClientInterceptors();
        Set<ClientInterceptor> interceptorSet = Sets.newHashSet();
        if (globalInterceptorList != null && !globalInterceptorList.isEmpty()) {
            interceptorSet.addAll(globalInterceptorList);
        }
        if (interceptors != null && !interceptors.isEmpty()) {
            interceptorSet.addAll(interceptors);
        }
        return ClientInterceptors.intercept(channel, Lists.newArrayList(interceptorSet));
    }
}
