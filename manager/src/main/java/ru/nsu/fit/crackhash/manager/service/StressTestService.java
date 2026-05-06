package ru.nsu.fit.crackhash.manager.service;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.nsu.fit.crackhash.manager.model.StressPhaseResult;
import ru.nsu.fit.crackhash.manager.model.StressReportDTO;
import ru.nsu.fit.crackhash.manager.model.StressStatsDTO;

import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class StressTestService {

    private final ManagerService managerService;
    private final String k6ScriptPath = "/app/k6-stress.js";
    
    @Getter
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Process k6Process;
    
    @Getter
    private final AtomicInteger successCount = new AtomicInteger(0);
    @Getter
    private final AtomicInteger errorCount = new AtomicInteger(0);
    @Getter
    private final AtomicInteger refusedCount = new AtomicInteger(0);
    @Getter
    private final AtomicLong totalLatency = new AtomicLong(0);

    @Getter
    private final List<StressPhaseResult> phaseHistory = new CopyOnWriteArrayList<>();
    @Getter
    private int currentConcurrency = 0;
    @Getter
    private long p95Latency = 0;
    @Getter
    private double requestRate = 0.0;
    
    @Getter
    private int breakingPoint = 0;
    @Getter
    private String conclusion = "Стресс-тест k6 ожидает запуска";
    @Getter
    private String lastLogLine = "Готов к запуску...";

    private long lastRpsCheck = 0;
    private final AtomicInteger rpsCounter = new AtomicInteger(0);

    // Regex for parsing k6 output
    private final Pattern vuPattern = Pattern.compile("(\\d+)/(\\d+) VUs");
    private final Pattern p95Pattern = Pattern.compile("http_req_duration.*p\\(95\\)=(\\d+\\.?\\d*)ms");
    private final Pattern rpsPattern = Pattern.compile("http_reqs.* (\\d+\\.?\\d*)/s");

    public StressTestService(ManagerService managerService) {
        this.managerService = managerService;
        extractScriptFromResources();
    }

    private void extractScriptFromResources() {
        try (InputStream is = getClass().getResourceAsStream("/k6-stress.js")) {
            if (is != null) {
                Files.copy(is, Paths.get(k6ScriptPath), StandardCopyOption.REPLACE_EXISTING);
                log.info("k6 script extracted to {}", k6ScriptPath);
            }
        } catch (Exception e) {
            log.warn("Could not extract k6 script to /app/k6-stress.js. Using local fallback.");
        }
    }

    public void startTest() {
        if (!running.compareAndSet(false, true)) {
            return;
        }

        successCount.set(0);
        errorCount.set(0);
        refusedCount.set(0);
        totalLatency.set(0);
        currentConcurrency = 0;
        p95Latency = 0;
        requestRate = 0.0;
        phaseHistory.clear();
        conclusion = "k6 тест запущен: Идет наращивание нагрузки до 10к VUs...";
        lastLogLine = "Инициализация движка k6...";

        CompletableFuture.runAsync(this::runK6);
    }

    private void runK6() {
        try {
            log.info("Starting k6 high-intensity load test...");
            // Use --quiet to avoid excessive TTY output, but still get stdout metrics
            List<String> command = List.of("k6", "run", "--summary-export=/app/summary.json", k6ScriptPath);
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(new File("/app"));
            pb.redirectErrorStream(true);
            
            k6Process = pb.start();
            
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(k6Process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    parseK6Line(line);
                }
            }
            
            int exitCode = k6Process.waitFor();
            log.info("k6 finished with exit code {}", exitCode);
            processK6Summary();
            
        } catch (Exception e) {
            log.error("k6 execution failed: {}", e.getMessage());
            conclusion = "Ошибка k6: " + e.getMessage();
        } finally {
            running.set(false);
            managerService.cancelAllTasks();
        }
    }

    private void parseK6Line(String line) {
        this.lastLogLine = line;

        // 1. Current VUs: "running (0.2s), 10/10000 VUs"
        Matcher vuMatcher = vuPattern.matcher(line);
        if (vuMatcher.find()) {
            this.currentConcurrency = Integer.parseInt(vuMatcher.group(1));
        }

        // 2. Average/P95 latency: "http_req_duration... avg=2.45ms p(95)=5.1ms"
        Matcher p95Matcher = p95Pattern.matcher(line);
        if (p95Matcher.find()) {
            this.p95Latency = (long) Double.parseDouble(p95Matcher.group(1));
        }

        // 3. RPS: "http_reqs... 120.5/s"
        Matcher rpsMatcher = rpsPattern.matcher(line);
        if (rpsMatcher.find()) {
            this.requestRate = Double.parseDouble(rpsMatcher.group(1));
        }

        // 4. Custom METRIC line for real-time tracking (from console.log in k6-stress.js)
        if (line.contains("METRIC: ")) {
            try {
                // Info: METRIC: LATENCY=2.45 STATUS=200 ERR=none
                if (line.contains("LATENCY=")) {
                    String latStr = line.substring(line.indexOf("LATENCY=") + 8, line.indexOf(" STATUS="));
                    double currentLat = Double.parseDouble(latStr);
                    totalLatency.addAndGet((long)currentLat);
                    
                    // Simple p95 estimate: if it's high, it affects p95. 
                    // To keep it simple without a full T-Digest, we'll store the max in the last bucket.
                    if (currentLat > this.p95Latency || Math.random() < 0.1) {
                        this.p95Latency = (long)currentLat;
                    }
                }
                
                if (line.contains("STATUS=200")) {
                    successCount.incrementAndGet();
                } else if (line.contains("STATUS=0") || line.contains("ERR=connection refused")) {
                    refusedCount.incrementAndGet();
                    errorCount.incrementAndGet();
                } else {
                    errorCount.incrementAndGet();
                }

                // Simple RPS estimation based on message frequency
                long now = System.currentTimeMillis();
                rpsCounter.incrementAndGet();
                if (now - lastRpsCheck > 1000) {
                    double seconds = (now - lastRpsCheck) / 1000.0;
                    this.requestRate = rpsCounter.get() / seconds;
                    rpsCounter.set(0);
                    lastRpsCheck = now;
                }
            } catch (Exception e) {
                // Ignore parse errors from k6 logs
            }
        }

        log.info("[k6 PRO] {}", line);
    }

    private void processK6Summary() {
        try {
            File summaryFile = new File("/app/summary.json");
            if (summaryFile.exists()) {
                String content = Files.readString(summaryFile.toPath());
                if (content.contains("\"fails\":0") || content.contains("\"fails\": 0")) {
                     conclusion = "Тест 10k VUs пройден. Система показала исключительную стабильность.";
                } else {
                     conclusion = "Тест выявил деградацию. Количество отказов значительно возросло при высокой нагрузке.";
                }
            } else {
                conclusion = "k6 завершен. Результаты доступны в логах консоли.";
            }
        } catch (Exception e) {
            log.error("Failed to process k6 summary: {}", e.getMessage());
        }
    }

    public void stopTest() {
        if (running.get() && k6Process != null) {
            k6Process.destroyForcibly();
            conclusion = "Тест остановлен пользователем.";
            running.set(false);
            managerService.cancelAllTasks();
        }
    }

    public StressStatsDTO getStats() {
        return new StressStatsDTO(
            running.get(),
            currentConcurrency,
            successCount.get(),
            errorCount.get(),
            refusedCount.get(),
            getAvgLatency(),
            p95Latency,
            requestRate,
            lastLogLine
        );
    }

    public StressReportDTO getReport() {
        StressReportDTO dto = new StressReportDTO();
        dto.setConclusion(conclusion);
        dto.setBreakingPoint(breakingPoint);
        dto.setTotalSuccess(successCount.get());
        dto.setTotalErrors(errorCount.get());
        dto.setPhases(new ArrayList<>(phaseHistory));
        return dto;
    }

    public long getAvgLatency() {
        int success = successCount.get();
        return success > 0 ? totalLatency.get() / success : 0;
    }
}
