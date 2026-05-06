package ru.nsu.fit.crackhash.manager.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class StressStatsDTO {
    private boolean running;
    private int concurrency;
    private int successCount;
    private int errorCount;
    private int refusedCount;
    private long avgLatency;
    private long p95Latency;
    private double requestRate;
    private String lastLogLine;
}
