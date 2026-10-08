package com.example.ClankerOrchestrator.opencode;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class OpenCodeManager {

    ProcessBuilder builder;
    Process process;

    public OpenCodeManager() {
        try {

            List<String> list = new ArrayList<String>();
            list.add("C:\\Users\\kalla\\AppData\\Roaming\\npm\\opencode.cmd");
            list.add("serve");
            list.add("--port");
            list.add("4096");

            ProcessBuilder builder = new ProcessBuilder(list);
            this.builder = builder;

        } catch (Exception e) {
            System.out.println(e);
        }
    }

    public void Start() {
        try {
            if (this.builder == null) {
                return;
            }

            Process process = this.builder.start();
            this.process = process;
            System.out.println(process.isAlive());

        } catch (Exception e) {
            System.out.println(e);
        }
    }

    public void Stop() {
        if (this.process.isAlive()) {
            this.process.destroy();
        }
    }

    public boolean isRunning() {
        return process != null && process.isAlive();
    }
}
