package ru.nsu.fit.crackhash.manager.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import ru.nsu.fit.crackhash.manager.model.CrackRequestDTO;
import ru.nsu.fit.crackhash.manager.model.RequestState;
import ru.nsu.fit.crackhash.manager.model.StatusResponseDTO;
import ru.nsu.fit.crackhash.model.CrackHashManagerRequest;
import ru.nsu.fit.crackhash.model.CrackHashWorkerResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
public class ManagerService {

    private static final Logger log = LoggerFactory.getLogger(ManagerService.class);

    // Создается потокобезопасная карта `requestId -> RequestState`, хранит все
    // задачи менеджера в памяти
    private final ConcurrentHashMap<String, RequestState> requests = new ConcurrentHashMap<>();
    private final RestTemplate restTemplate = new RestTemplate();
    // Объявляется поле пула потоков
    private final java.util.concurrent.ExecutorService dispatchExecutor;

    // Принимает размер пула потоков из конфигурации (по умолчанию 600) и инициализирует пул.
    public ManagerService(@Value("${crackhash.manager.dispatch-pool-size:600}") int poolSize) {
        this.dispatchExecutor = java.util.concurrent.Executors.newFixedThreadPool(poolSize);
    }

    @Value("#{'${crackhash.worker-urls}'.split(',')}")
    private List<String> workerUrls;

    @Value("${crackhash.timeout:600000}")
    private long timeoutMillis;

    private static final List<String> ALPHABET_SYMBOLS = Arrays.asList(
            "a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m",
            "n", "o", "p", "q", "r", "s", "t", "u", "v", "w", "x", "y", "z",
            "0", "1", "2", "3", "4", "5", "6", "7", "8", "9");

    public String crackHash(CrackRequestDTO request) {
        int partCount = workerUrls.size();
        String requestId = UUID.randomUUID().toString();
        requests.put(requestId, new RequestState(partCount));
        log.info("Task {} accepted. Hash: {}, MaxLen: {}. Splitting into {} parts.", requestId, request.getHash(),
                request.getMaxLength(), partCount);

        // Создается список будущих асинхронных операций
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (int partNumber = 1; partNumber <= partCount; partNumber++) {
            final int pNum = partNumber;
            // Формируется заготовка межсервисного запроса к воркеру
            CrackHashManagerRequest managerRequest = new CrackHashManagerRequest();
            managerRequest.setRequestId(requestId);
            managerRequest.setPartNumber(pNum);
            managerRequest.setPartCount(partCount);
            managerRequest.setHash(request.getHash());
            managerRequest.setMaxLength(request.getMaxLength());

            CrackHashManagerRequest.Alphabet alphabet = new CrackHashManagerRequest.Alphabet();
            alphabet.getSymbols().addAll(ALPHABET_SYMBOLS);
            managerRequest.setAlphabet(alphabet);

            String targetUrl = workerUrls.get((pNum - 1) % workerUrls.size());

            futures.add(CompletableFuture.runAsync(() -> {
                log.debug("Dispatching Part {} of Task {} to Worker at {}", pNum, requestId, targetUrl);
                org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
                headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
                org.springframework.http.HttpEntity<CrackHashManagerRequest> entity = new org.springframework.http.HttpEntity<>(
                        managerRequest, headers);
                restTemplate.postForEntity(targetUrl, entity, Void.class);
            }, dispatchExecutor));
        }

        try {
            // Ждем подтверждения воркеров
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("Failed to dispatch Task {} to workers: {}", requestId, e.getMessage());
            requests.remove(requestId);
            throw new RuntimeException("Worker cluster unavailable or timeout");
        }

        return requestId;
    }

    public StatusResponseDTO getStatus(String requestId) {
        RequestState state = requests.get(requestId);
        if (state == null) {
            return new StatusResponseDTO("ERROR", null);
        }

        List<String> data = null;
        if (state.getStatus() == RequestState.Status.READY) {
            data = state.getData();
        }
        return new StatusResponseDTO(state.getStatus().name(), data);
    }

    public void processWorkerResponse(CrackHashWorkerResponse response) {
        RequestState state = requests.get(response.getRequestId());
        int count = response.getAnswers() != null ? response.getAnswers().getWords().size() : 0;
        log.info("Manager received Part {} for Task {} from Worker. Matches found: {}", response.getPartNumber(),
                response.getRequestId(), count);

        if (state != null) {
            if (response.getAnswers() != null) {
                state.addData(response.getAnswers().getWords());
            }
            state.completePart();
        } else {
            log.warn("Received response for unknown/expired Task {}", response.getRequestId());
        }
    }

    public void cancelTask(String requestId) {
        RequestState state = requests.get(requestId);
        if (state != null && state.getStatus() == RequestState.Status.IN_PROGRESS) {
            state.cancel();
            log.info("Task {} cancelled by user application. Broadcasting cancellation to workers.", requestId);
            for (String url : workerUrls) {
                String cancelUrl = url.replace("/task", "/cancel") + "?requestId=" + requestId;
                dispatchExecutor.submit(() -> {
                    try {
                        restTemplate.postForEntity(cancelUrl, null, Void.class);
                    } catch (Exception e) {
                        log.warn("Failed to send cancellation to {}: {}", cancelUrl, e.getMessage());
                    }
                });
            }
        }
    }

    public void cancelAllTasks() {
        log.info("Global cancellation triggered. Stopping all active tasks.");
        // Очистка состояния на стороне менеджера
        requests.forEach((id, state) -> {
            if (state.getStatus() == RequestState.Status.IN_PROGRESS) {
                state.cancel();
            }
        });

        for (String url : workerUrls) {
            String cancelAllUrl = url.replace("/task", "/cancel-all");
            dispatchExecutor.submit(() -> {
                try {
                    log.info("Sending cancellation to {}", cancelAllUrl);
                    restTemplate.postForEntity(cancelAllUrl, null, Void.class);
                } catch (Exception e) {
                    log.warn("Failed to send cancellation to {}: {}", cancelAllUrl, e.getMessage());
                }
            });
        }
    }

    // Запускается раз в 10 сек
    @Scheduled(fixedDelay = 10000)
    public void handleTimeouts() {
        requests.forEach((id, state) -> {
            if (state.getStatus() == RequestState.Status.IN_PROGRESS) {
                state.isTimeout(timeoutMillis);
                if (state.getStatus() == RequestState.Status.ERROR) {
                    log.error("Task {} timed out.", id);
                }
            }
        });
    }
}
