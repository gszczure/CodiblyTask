package org.codibly;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.feign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients(basePackages = "org.codibly.externalClient")
public class Main {
    public static void main(String[] args) {
        System.out.println("Hello world!");
    }
}