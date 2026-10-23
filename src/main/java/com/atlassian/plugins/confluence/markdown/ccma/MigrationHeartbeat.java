package com.atlassian.plugins.confluence.markdown.ccma;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Periodically runs a beat (an upload of dummy app data) so CCMA does not consider the transfer settled after
 * 15 minutes of inactivity, e.g. while iterating over many spaces that have nothing to migrate.
 *
 * The heartbeat stops when it is closed (the migration settled, successfully or not), when a beat fails with an
 * exception that means the transfer was settled by CCMA, or when the timeout elapses, whichever comes first.
 */
class MigrationHeartbeat implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(MigrationHeartbeat.class);
    // Thrown by the CCMA agent (not on our compile classpath) for the 4XX response of a settled transfer
    private static final String HTTP_SERVICE_EXCEPTION = "com.atlassian.migration.agent.okhttp.HttpServiceException";

    private final Runnable beat;
    private final Duration interval;
    private final Duration timeout;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        final Thread thread = new Thread(runnable, "iframes-migration-heartbeat");
        thread.setDaemon(true);
        return thread;
    });

    MigrationHeartbeat(Runnable beat, Duration interval, Duration timeout) {
        this.beat = beat;
        this.interval = interval;
        this.timeout = timeout;
    }

    MigrationHeartbeat start() {
        executor.scheduleWithFixedDelay(this::tick, interval.toMillis(), interval.toMillis(), TimeUnit.MILLISECONDS);
        executor.schedule(this::expire, timeout.toMillis(), TimeUnit.MILLISECONDS);
        return this;
    }

    private void tick() {
        try {
            beat.run();
        } catch (Exception e) {
            if (isTransferSettled(e)) {
                log.warn("Transfer was settled, stopping migration heartbeat", e);
                executor.shutdownNow();
                return;
            }
            // Never let an exception escape, it would silently cancel all later beats
            log.warn("Failed to upload migration heartbeat", e);
        }
    }

    /**
     * Failsafe rethrows the last failure once retries are exhausted, wrapped in FailsafeException if it is checked,
     * so look for the CCMA exception through the whole cause chain.
     */
    static boolean isTransferSettled(Throwable throwable) {
        for (Throwable t = throwable; t != null; t = t.getCause() == t ? null : t.getCause()) {
            for (Class<?> c = t.getClass(); c != null; c = c.getSuperclass()) {
                if (HTTP_SERVICE_EXCEPTION.equals(c.getName())) {
                    return true;
                }
            }
        }
        return false;
    }

    private void expire() {
        log.warn("Migration heartbeat timed out after {}, stopping it", timeout);
        executor.shutdownNow();
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }
}
