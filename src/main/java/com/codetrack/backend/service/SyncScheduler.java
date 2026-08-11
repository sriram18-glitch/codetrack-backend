package com.codetrack.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Background daily synchronization. Every day at 09:00 (server local time)
 * every student that has a coding profile is re-synced through the shared
 * {@link DailySyncService}. A single student's failure is logged and
 * processing continues with the next student, so the scheduled run always
 * completes. The same logic is reused by the external cron-trigger endpoint.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SyncScheduler {

    private final DailySyncService dailySyncService;

    @Scheduled(cron = "${codetrack.sync.cron:0 0 9 * * *}")
    public void automaticDailySync() {
        dailySyncService.runDailySync(DailySyncService.SOURCE_AUTOMATIC);
    }
}
