package com.example.worker.agent.application.service;

import com.example.worker.agent.application.exception.AgentExecutionException;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Shared utility for running external processes.
 * Drains stdout in a virtual thread to prevent pipe-buffer deadlock
 * when process output exceeds the OS pipe buffer size.
 */
class ProcessRunner {

    private ProcessRunner() {}

    static String run(String workDir, long timeout, TimeUnit unit, String... cmd) {
        AtomicReference<String> outputRef = new AtomicReference<>("");
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd)
                    .directory(new File(workDir))
                    .redirectErrorStream(true);

            Process process = pb.start();

            Thread reader = Thread.ofVirtual().start(() -> {
                try {
                    outputRef.set(new String(process.getInputStream().readAllBytes()));
                } catch (IOException ignored) {}
            });

            boolean finished = process.waitFor(timeout, unit);
            if (!finished) {
                process.destroyForcibly();
                reader.interrupt();
                throw new AgentExecutionException(cmd[0] + " timed out after " + timeout + " " + unit);
            }

            reader.join();

            if (process.exitValue() != 0) {
                throw new AgentExecutionException(cmd[0] + " failed: " + outputRef.get().trim());
            }
            return outputRef.get();
        } catch (AgentExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new AgentExecutionException(cmd[0] + " error", e);
        }
    }
}
