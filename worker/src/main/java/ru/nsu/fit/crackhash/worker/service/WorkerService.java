package ru.nsu.fit.crackhash.worker.service;

import org.paukov.combinatorics3.Generator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import ru.nsu.fit.crackhash.model.CrackHashManagerRequest;
import ru.nsu.fit.crackhash.model.CrackHashWorkerResponse;
import ru.nsu.fit.crackhash.model.CrackHashWorkerResponse.Answers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.bind.DatatypeConverter;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

@Service
public class WorkerService {

    private static final Logger log = LoggerFactory.getLogger(WorkerService.class);

    @Value("${MANAGER_URL}")
    private String managerUrl;

    private final RestTemplate restTemplate = new RestTemplate();
    private final Set<String> cancelledRequests = ConcurrentHashMap.newKeySet();
    private final ExecutorService executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    private volatile boolean globalCancellationActive = false;

    public void cancelTask(String requestId) {
        log.info("Received cancellation signal for Task {}", requestId);
        cancelledRequests.add(requestId);
    }

    public void cancelAllTasks() {
        log.info("Worker received GLOBAL cancellation signal. Clearing all pending tasks.");
        globalCancellationActive = true;
        if (executorService instanceof ThreadPoolExecutor) {
            ((ThreadPoolExecutor) executorService).getQueue().clear();
        }
        // Ждем 2 сек и сбрасываем флаг для готовности к новым задачам
        new Thread(() -> {
            try { Thread.sleep(2000); } catch (InterruptedException e) {}
            globalCancellationActive = false;
            cancelledRequests.clear();
        }).start();
    }

    public void processTask(CrackHashManagerRequest request) {
        executorService.submit(() -> {
            long startTime = System.currentTimeMillis();
            try {
                if (globalCancellationActive || cancelledRequests.contains(request.getRequestId())) {
                    log.info("Task {} ignored due to global cancellation or specific skip.", request.getRequestId());
                    return;
                }
                
                log.info("Worker started Part {} of Task {}. Alphabet size: {}, MaxLen: {}", 
                    request.getPartNumber(), request.getRequestId(), request.getAlphabet().getSymbols().size(), request.getMaxLength());
                
                List<String> foundWords = performBruteForce(request, startTime);
                
                if (globalCancellationActive || cancelledRequests.contains(request.getRequestId())) {
                    log.info("Task {} was cancelled during execution. Aborting result dispatch.", request.getRequestId());
                    return;
                }
                
                sendResult(request.getRequestId(), request.getPartNumber(), foundWords);
                double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                log.info("Worker finished Part {} of Task {}. Found {} matches in {}s.", 
                    request.getPartNumber(), request.getRequestId(), foundWords.size(), duration);
            } catch (Exception e) {
                log.error("Error working on task {}: {}", request.getRequestId(), e.getMessage());
                sendResult(request.getRequestId(), request.getPartNumber(), new ArrayList<>());
            }
        });
    }

    private List<String> performBruteForce(CrackHashManagerRequest request, long startTime) throws NoSuchAlgorithmException {
        List<String> results = new ArrayList<>();
        MessageDigest md5 = MessageDigest.getInstance("MD5");
        
        int partNumber = request.getPartNumber();
        int partCount = request.getPartCount();
        List<String> alphabet = request.getAlphabet().getSymbols();
        final String reqId = request.getRequestId();
        final String targetHash = request.getHash().toLowerCase();

        for (int length = 1; length <= request.getMaxLength(); length++) {
            if (globalCancellationActive || cancelledRequests.contains(reqId)) break;
            
            long totalCombinations = (long) Math.pow(alphabet.size(), length);
            long limit = totalCombinations / partCount;
            long skip = (partNumber - 1) * limit;

            if (partNumber == partCount) {
                limit = totalCombinations - skip;
            }

            log.info("Part {}: Length {}. Total: {}, Skipping: {}, Limit: {}", partNumber, length, totalCombinations, skip, limit);

            Iterator<List<String>> it = Generator.permutation(alphabet)
                    .withRepetitions(length)
                    .iterator();

            // Пропуск чужих комбинаций
            for (long i = 0; i < skip; i++) {
                if (i % 100000 == 0 && (globalCancellationActive || cancelledRequests.contains(reqId))) return results;
                if (it.hasNext()) it.next();
            }

            // Processing loop
            for (long i = 0; i < limit; i++) {
                if (i % 100000 == 0 && (globalCancellationActive || cancelledRequests.contains(reqId))) return results;
                if (!it.hasNext()) break;
                
                List<String> wordList = it.next();
                String word = String.join("", wordList);
                
                md5.reset();
                md5.update(word.getBytes());
                byte[] digest = md5.digest();
                String hash = DatatypeConverter.printHexBinary(digest).toLowerCase();
                
                if (hash.equals(targetHash)) {
                    double foundAt = (System.currentTimeMillis() - startTime) / 1000.0;
                    results.add(word + " (" + foundAt + "s)");
                }
            }
        }
        return results;
    }

    private void sendResult(String requestId, int partNumber, List<String> words) {
        CrackHashWorkerResponse response = new CrackHashWorkerResponse();
        response.setRequestId(requestId);
        response.setPartNumber(partNumber);
        
        Answers answers = new Answers();
        if (words != null && !words.isEmpty()) {
            answers.getWords().addAll(words);
        }
        response.setAnswers(answers);
        
        try {
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            org.springframework.http.HttpEntity<CrackHashWorkerResponse> entity = new org.springframework.http.HttpEntity<>(response, headers);
            
            restTemplate.setRequestFactory(new org.springframework.http.client.HttpComponentsClientHttpRequestFactory());
            restTemplate.patchForObject(managerUrl, entity, Void.class);
        } catch (Exception e) {
            log.error("Error communicating with manager for task {}: {}", requestId, e.getMessage());
        }
    }
}
