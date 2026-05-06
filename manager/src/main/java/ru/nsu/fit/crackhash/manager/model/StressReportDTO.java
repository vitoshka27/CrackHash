package ru.nsu.fit.crackhash.manager.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StressReportDTO {
    private List<StressPhaseResult> phases;
    private int breakingPoint;
    private String conclusion;
    private int totalSuccess;
    private int totalErrors;
    private Map<String, Integer> errorDetails;
}
