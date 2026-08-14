package com.codetrack.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Background daily synchronization. Every day at 09:00 (server local time)
 * every student is re-synced through the shared {@link BulkSyncService}). The
 * scheduled run only fires while the JVM is awake; it is a fallback on top of
 * the external cron-trigger endpoint. If another bulk run is already in
 * progress the duplicate trigger is skipped and logged.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SyncScheduler {

    private final BulkSyncService bulkSyncService;

    @Scheduled(cron = "${codetrack.sync.cron:0 0 9 * * *}")
    public void automaticDailySync() {
        try {
            bulkSyncService.submit(BulkSyncService.ALL, BulkSyncService.SOURCE_AUTOMATIC);
        } catch (Exception ex) {
            log.info("Automatic daily sync skipped — a synchronization is already running");
        }
    }
}