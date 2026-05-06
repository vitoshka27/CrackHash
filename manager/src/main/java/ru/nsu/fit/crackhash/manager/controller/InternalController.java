package ru.nsu.fit.crackhash.manager.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.nsu.fit.crackhash.manager.service.ManagerService;
import ru.nsu.fit.crackhash.model.CrackHashWorkerResponse;

@RestController
@RequestMapping("/internal/api/manager/hash/crack")
public class InternalController {

    private final ManagerService managerService;

    public InternalController(ManagerService managerService) {
        this.managerService = managerService;
    }

    @PatchMapping(value = "/request", consumes = "application/json")
    public ResponseEntity<Void> processWorkerResponse(@RequestBody CrackHashWorkerResponse response) {
        managerService.processWorkerResponse(response);
        return ResponseEntity.ok().build();
    }
}
