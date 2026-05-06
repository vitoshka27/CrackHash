package ru.nsu.fit.crackhash.manager.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StressPhaseResult {
    private int concurrency;
    private int successCount;
    private int errorCount;
    private long avgLatency;
    private double errorRate;
}
