package ru.nsu.fit.crackhash.worker.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.nsu.fit.crackhash.model.CrackHashManagerRequest;
import ru.nsu.fit.crackhash.worker.service.WorkerService;

@RestController
@RequestMapping("/internal/api/worker/hash/crack")
public class WorkerController {

    private final WorkerService workerService;

    public WorkerController(WorkerService workerService) {
        this.workerService = workerService;
    }

    @PostMapping(value = "/task", consumes = "application/json")
    public ResponseEntity<Void> processTask(@RequestBody CrackHashManagerRequest request) {
        workerService.processTask(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping(value = "/cancel")
    public ResponseEntity<Void> cancelTask(@RequestParam("requestId") String requestId) {
        workerService.cancelTask(requestId);
        return ResponseEntity.ok().build();
    }

    @PostMapping(value = "/cancel-all")
    public ResponseEntity<Void> cancelAllTasks() {
        workerService.cancelAllTasks();
        return ResponseEntity.ok().build();
    }
}
