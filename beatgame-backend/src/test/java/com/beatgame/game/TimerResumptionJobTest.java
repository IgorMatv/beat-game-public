package com.beatgame.game;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TimerResumptionJobTest {

    @Mock RoundService roundService;

    TimerResumptionJob job;

    @BeforeEach
    void setUp() {
        job = new TimerResumptionJob(roundService);
    }

    @Test
    void run_resumesTimersOnStartup() throws Exception {
        job.run(null);

        verify(roundService).resumeTimersAfterRestart();
    }
}
