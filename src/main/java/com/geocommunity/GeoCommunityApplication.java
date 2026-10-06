package com.geocommunity;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class GeoCommunityApplication {

    public static void main(String[] args) {
        SpringApplication.run(GeoCommunityApplication.class, args);
    }
}
