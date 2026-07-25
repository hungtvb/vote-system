package com.hungtvb.votesystem;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class VoteSystemApplication {
    public static void main(String[] args) {
        SpringApplication.run(VoteSystemApplication.class, args);
    }
}
