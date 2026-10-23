package com.atlassian.plugins.confluence.markdown.ccma;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.atlassian.migration.agent.okhttp.HttpServiceException;
import dev.failsafe.FailsafeException;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class MigrationHeartbeatTest {

    @Test
    void beatsPeriodicallyUntilClosed() throws Exception {
        final AtomicInteger beats = new AtomicInteger();
        final MigrationHeartbeat heartbeat =
                new MigrationHeartbeat(beats::incrementAndGet, Duration.ofMillis(20), Duration.ofSeconds(10)).start();

        Thread.sleep(200);
        heartbeat.close();
        final int beatsAtClose = beats.get();
        Thread.sleep(100);

        assertTrue(beatsAtClose > 1);
        assertEquals(beatsAtClose, beats.get());
    }

    @Test
    void stopsAfterTimeout() throws Exception {
        final AtomicInteger beats = new AtomicInteger();
        try (MigrationHeartbeat ignored =
                     new MigrationHeartbeat(beats::incrementAndGet, Duration.ofMillis(20), Duration.ofMillis(100)).start()) {
            Thread.sleep(250);
            final int beatsAfterTimeout = beats.get();
            Thread.sleep(100);

            assertTrue(beatsAfterTimeout > 0);
            assertEquals(beatsAfterTimeout, beats.get());
        }
    }

    @Test
    void keepsBeatingAfterFailedBeat() throws Exception {
        final AtomicInteger beats = new AtomicInteger();
        try (MigrationHeartbeat ignored = new MigrationHeartbeat(() -> {
            if (beats.incrementAndGet() == 1) {
                throw new IllegalStateException("boom");
            }
        }, Duration.ofMillis(20), Duration.ofSeconds(10)).start()) {
            Thread.sleep(200);

            assertTrue(beats.get() > 1);
        }
    }

    @Test
    void stopsWhenTransferSettled() throws Exception {
        final AtomicInteger beats = new AtomicInteger();
        try (MigrationHeartbeat ignored = new MigrationHeartbeat(() -> {
            beats.incrementAndGet();
            throw new FailsafeException(new HttpServiceException());
        }, Duration.ofMillis(20), Duration.ofSeconds(10)).start()) {
            Thread.sleep(200);

            assertEquals(1, beats.get());
        }
    }

    @Test
    void onlyHttpServiceExceptionMeansTransferSettled() {
        assertTrue(MigrationHeartbeat.isTransferSettled(new HttpServiceException()));
        assertTrue(MigrationHeartbeat.isTransferSettled(new FailsafeException(new HttpServiceException())));
        assertFalse(MigrationHeartbeat.isTransferSettled(new IllegalStateException("boom")));
    }
}
