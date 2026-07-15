package com.example.worker.engine.application.contract.v1;

public record AttemptHistoryResponse(
        boolean recorded,
        int version
) {
}
