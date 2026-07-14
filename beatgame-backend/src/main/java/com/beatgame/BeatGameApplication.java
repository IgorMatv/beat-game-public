package com.beatgame;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BeatGameApplication {
    public static void main(String[] args) {
        SpringApplication.run(BeatGameApplication.class, args);
    }
}
