package com.beatgame.game;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

// Runs once, right after the application context finishes starting up. Redis game
// state survives a restart fine; the in-memory round/ready timers that were supposed
// to expire it don't (issue #29) — this is what re-arms them.
@Component
public class TimerResumptionJob implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TimerResumptionJob.class);

    private final RoundService roundService;

    public TimerResumptionJob(RoundService roundService) {
        this.roundService = roundService;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("Resuming round/ready timers for any in-progress games after restart");
        roundService.resumeTimersAfterRestart();
    }
}
