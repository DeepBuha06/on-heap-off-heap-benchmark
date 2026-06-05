package com.faststore.kvstore.config;

import com.faststore.kvstore.server.NettyTcpServer;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class NettyConfig {

    private final NettyTcpServer nettyServer;

    public NettyConfig(NettyTcpServer nettyServer) {
        this.nettyServer = nettyServer;
    }

    @Bean
    public CommandLineRunner startNettyServer() {
        return args -> {
            // Run Netty on a different thread so it doesn't block Spring Boot startup
            new Thread(() -> {
                try {
                    nettyServer.start(8081);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    e.printStackTrace();
                }
            }, "Netty-Startup-Thread").start();
        };
    }
}
