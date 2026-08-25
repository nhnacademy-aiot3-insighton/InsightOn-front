package com.nhnacademy.insightonfront;

import com.nhnacademy.insightonfront.config.FeignGlobalConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableFeignClients(defaultConfiguration = FeignGlobalConfig.class)
@EnableScheduling
public class InsightonFrontApplication {

    public static void main(String[] args) {
        SpringApplication.run(InsightonFrontApplication.class, args);
    }

}
