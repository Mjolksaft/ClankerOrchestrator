package com.example.ClankerOrchestrator.opencode;

import com.example.ClankerOrchestrator.dto.OpenCodeStatus;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController 
@RequestMapping("/OpenCode")
public class OpenCodeController {

    private final OpenCodeService openCodeService;

    
    public OpenCodeController(OpenCodeService openCodeService) {
        this.openCodeService = openCodeService;
    }

    @PostMapping("/start")
    public ResponseEntity<OpenCodeStatus> StartOpenCode() {
        boolean running = openCodeService.StartOpenCode();

        return running
            ? ResponseEntity.ok(new OpenCodeStatus(true))
            : ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new OpenCodeStatus(false));
    }

    @PostMapping("/stop") 
    public ResponseEntity<OpenCodeStatus> StopOpenCode() {
        boolean running = openCodeService.StopOpenCode();

        return running
            ? ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new OpenCodeStatus(true))
            : ResponseEntity.ok(new OpenCodeStatus(false));
    }

    @GetMapping("/status")
    public ResponseEntity<OpenCodeStatus> StatusOpenCode() {
        return ResponseEntity.ok(new OpenCodeStatus(openCodeService.IsRunningOpenCode()));
    }

}