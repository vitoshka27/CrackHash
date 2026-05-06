package ru.nsu.fit.crackhash.manager.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class RequestState {
    public enum Status {
        IN_PROGRESS, READY, ERROR, NOT_FOUND, CANCELLED
    }

    private Status status = Status.IN_PROGRESS;
    private final List<String> data = new ArrayList<>();
    private final Instant startTime = Instant.now();
    private final AtomicInteger remainingParts;

    public RequestState(int parts) {
        this.remainingParts = new AtomicInteger(parts);
    }

    public synchronized void addData(List<String> words) {
        if (status == Status.CANCELLED) return;
        if (words != null && !words.isEmpty()) {
            data.addAll(words);
        }
    }

    public synchronized void completePart() {
        if (status == Status.CANCELLED) return;
        if (remainingParts.decrementAndGet() == 0 && status == Status.IN_PROGRESS) {
            status = data.isEmpty() ? Status.NOT_FOUND : Status.READY;
        }
    }

    public synchronized void cancel() {
        if (status == Status.IN_PROGRESS) {
            this.status = Status.CANCELLED;
        }
    }

    public synchronized void markError() {
        status = Status.ERROR;
    }

    public synchronized boolean isTimeout(long timeoutMillis) {
        if (status == Status.IN_PROGRESS && Instant.now().minusMillis(timeoutMillis).isAfter(startTime)) {
            status = Status.ERROR;
            return true;
        }
        return false;
    }

    public synchronized Status getStatus() {
        return status;
    }

    public synchronized List<String> getData() {
        return new ArrayList<>(data);
    }
}
