package ru.nsu.fit.crackhash.manager.model;

public class CrackResponseDTO {
    private String requestId;

    public CrackResponseDTO(String requestId) {
        this.requestId = requestId;
    }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
