package com.xhs.canal;

import com.alibaba.otter.canal.client.CanalConnectors;
import com.alibaba.otter.canal.client.CanalConnector;
import com.alibaba.otter.canal.protocol.Message;
import com.xhs.config.CanalSyncProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class CanalBinlogClient {

    private final CanalSyncProperties properties;
    private final CanalChangeDispatcher changeDispatcher;

    private volatile boolean running;
    private ExecutorService executor;

    @PostConstruct
    public void start() {
        if (!properties.isEnabled()) {
            log.info("Canal binlog sync is disabled");
            return;
        }
        running = true;
        executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "xiaohongshu-canal-binlog-client");
            thread.setDaemon(true);
            return thread;
        });
        executor.submit(this::consumeLoop);
    }

    @PreDestroy
    public void stop() {
        running = false;
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    private void consumeLoop() {
        long backoff = properties.getInitialBackoffMillis();
        while (running && !Thread.currentThread().isInterrupted()) {
            CanalConnector connector = null;
            try {
                connector = newConnector();
                connector.connect();
                connector.subscribe(properties.getSubscribe());
                connector.rollback();
                log.info("Connected to Canal server {}:{}, destination={}, subscribe={}",
                        properties.getHost(), properties.getPort(), properties.getDestination(), properties.getSubscribe());
                backoff = properties.getInitialBackoffMillis();

                while (running && !Thread.currentThread().isInterrupted()) {
                    Message message = connector.getWithoutAck(properties.getBatchSize());
                    long batchId = message.getId();
                    int size = message.getEntries() == null ? 0 : message.getEntries().size();
                    if (batchId == -1 || size == 0) {
                        sleep(properties.getIdleSleepMillis());
                        continue;
                    }
                    try {
                        changeDispatcher.dispatch(message.getEntries());
                        connector.ack(batchId);
                        backoff = properties.getInitialBackoffMillis();
                    } catch (Exception e) {
                        connector.rollback(batchId);
                        log.error("Canal batch sync failed, rollback batchId={}, size={}", batchId, size, e);
                        sleep(nextBackoff(backoff));
                        backoff = growBackoff(backoff);
                    }
                }
            } catch (Exception e) {
                log.warn("Canal connection loop failed, will reconnect in {} ms", backoff, e);
                sleep(backoff);
                backoff = growBackoff(backoff);
            } finally {
                disconnect(connector);
            }
        }
    }

    private CanalConnector newConnector() {
        return CanalConnectors.newSingleConnector(
                new InetSocketAddress(properties.getHost(), properties.getPort()),
                properties.getDestination(),
                properties.getUsername(),
                properties.getPassword()
        );
    }

    private long growBackoff(long current) {
        long next = Math.max(properties.getInitialBackoffMillis(), current) * 2;
        return Math.min(next, properties.getMaxBackoffMillis());
    }

    private long nextBackoff(long current) {
        return Math.min(Math.max(properties.getInitialBackoffMillis(), current), properties.getMaxBackoffMillis());
    }

    private void disconnect(CanalConnector connector) {
        if (connector == null) {
            return;
        }
        try {
            connector.disconnect();
        } catch (Exception e) {
            log.debug("Disconnect Canal connector failed", e);
        }
    }

    private void sleep(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            TimeUnit.MILLISECONDS.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
