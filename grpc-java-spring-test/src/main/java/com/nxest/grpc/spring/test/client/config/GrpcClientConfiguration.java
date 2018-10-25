package com.nxest.grpc.spring.test.client.config;

import com.nxest.grpc.spring.client.*;
import com.nxest.grpc.spring.client.configure.GrpcChannelProperties;
import com.nxest.grpc.spring.test.server.config.GrpcServerConfiguration;
import io.grpc.LoadBalancer;
import io.grpc.util.RoundRobinLoadBalancerFactory;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
//@EnableConfigurationProperties
//@ConditionalOnClass({GrpcChannelFactory.class})
@AutoConfigureAfter(GrpcServerConfiguration.class)
public class GrpcClientConfiguration {

    //    @ConditionalOnMissingBean
    @Bean
    public GrpcChannelProperties grpcChannelProperties() {
        return new GrpcChannelProperties();
    }

    @Bean
    public GlobalClientInterceptorRegistry globalClientInterceptorRegistry() {
        return new GlobalClientInterceptorRegistry();
    }

    //    @ConditionalOnMissingBean
    @Bean
    public LoadBalancer.Factory grpcLoadBalancerFactory() {
        return RoundRobinLoadBalancerFactory.getInstance();
    }

    @Bean
    public GrpcChannelFactory addressChannelFactory() {
        return new AddressChannelFactory(grpcChannelProperties(), grpcLoadBalancerFactory(), globalClientInterceptorRegistry());
    }

    @Bean
    @ConditionalOnClass(GrpcClient.class)
    public GrpcClientBeanPostProcessor grpcClientBeanPostProcessor(ListableBeanFactory beanFactory, GrpcChannelFactory channelFactory) {
        return new GrpcClientBeanPostProcessor(beanFactory, channelFactory);
    }
}
