package com.example.ClankerOrchestrator.opencode;

import org.springframework.stereotype.Service;

@Service 
public class OpenCodeService {
    private final OpenCodeManager openCodeManager;

    public OpenCodeService(OpenCodeManager openCodeManager) {
        this.openCodeManager = openCodeManager;
    }
    
    public Boolean StartOpenCode() {
        if(!openCodeManager.isRunning()) {
            openCodeManager.Start();
        }

        return openCodeManager.isRunning();
    }

    public Boolean StopOpenCode() {
        if(openCodeManager.isRunning()) {
            openCodeManager.Stop();
        }
        
        return openCodeManager.isRunning();
    }

    public Boolean IsRunningOpenCode() {
        return openCodeManager.isRunning();
    }
}
