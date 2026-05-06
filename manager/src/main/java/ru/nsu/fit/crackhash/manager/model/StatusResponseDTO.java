package ru.nsu.fit.crackhash.manager.model;

import java.util.List;

public class StatusResponseDTO {
    private String status;
    private List<String> data;

    public StatusResponseDTO(String status, List<String> data) {
        this.status = status;
        this.data = data;
    }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public List<String> getData() { return data; }
    public void setData(List<String> data) { this.data = data; }
}
