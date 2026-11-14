package com.mamashop.logistics.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(EcpayLogisticsProperties.class)
public class LogisticsConfig {
}
