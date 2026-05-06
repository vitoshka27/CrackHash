package ru.nsu.fit.crackhash.manager.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.nsu.fit.crackhash.manager.model.CrackRequestDTO;
import ru.nsu.fit.crackhash.manager.model.CrackResponseDTO;
import ru.nsu.fit.crackhash.manager.model.StatusResponseDTO;
import ru.nsu.fit.crackhash.manager.model.StressStatsDTO;
import ru.nsu.fit.crackhash.manager.service.ManagerService;
import ru.nsu.fit.crackhash.manager.service.StressTestService;

import ru.nsu.fit.crackhash.manager.model.StressReportDTO;

@RestController
@RequestMapping("/api/hash")
public class ClientController {

    private final ManagerService managerService;
    private final StressTestService stressTestService;

    public ClientController(ManagerService managerService, StressTestService stressTestService) {
        this.managerService = managerService;
        this.stressTestService = stressTestService;
    }

    @PostMapping("/stress/start")
    public ResponseEntity<Void> startStress() {
        stressTestService.startTest();
        return ResponseEntity.ok().build();
    }

    @PostMapping("/stress/stop")
    public ResponseEntity<Void> stopStress() {
        stressTestService.stopTest();
        return ResponseEntity.ok().build();
    }

    @GetMapping("/stress/report")
    public ResponseEntity<StressReportDTO> getStressReport() {
        return ResponseEntity.ok(stressTestService.getReport());
    }

    @GetMapping("/stress/stats")
    public ResponseEntity<StressStatsDTO> getStressStats() {
        return ResponseEntity.ok(stressTestService.getStats());
    }

    @PostMapping(value = "/crack", consumes = "application/json", produces = "application/json")
    public ResponseEntity<CrackResponseDTO> crackHash(@RequestBody CrackRequestDTO request) {
        String requestId = managerService.crackHash(request);
        return ResponseEntity.ok(new CrackResponseDTO(requestId));
    }

    @GetMapping(value = "/status", produces = "application/json")
    public ResponseEntity<StatusResponseDTO> getStatus(@RequestParam("requestId") String requestId) {
        StatusResponseDTO status = managerService.getStatus(requestId);
        return ResponseEntity.ok(status);
    }

    @PostMapping("/cancel")
    public ResponseEntity<Void> cancelTask(@RequestParam("requestId") String requestId) {
        managerService.cancelTask(requestId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/cancel-all")
    public ResponseEntity<Void> cancelAllTasks() {
        managerService.cancelAllTasks();
        return ResponseEntity.ok().build();
    }
}
