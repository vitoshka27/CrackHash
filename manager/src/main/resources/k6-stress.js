import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
    stages: [
        { duration: '20s', target: 200 },   // Phase 1: Full Tomcat Utilization
        { duration: '40s', target: 1000 },  // Phase 2: Backlog Saturation (Queuing)
        { duration: '1m', target: 10000 },  // Phase 3: Extreme Stress (Connection Refusals)
        { duration: '20s', target: 0 },     // Phase 4: Recovery
    ],
    // Increase timeout to 10s to see "Long Response" vs "Refused"
    setupTimeout: '20s',
};

export default function () {
    const url = 'http://localhost:8085/api/hash/crack';
    const payload = JSON.stringify({
        hash: '81dc9bdb52d04dc20036dbd8313ed055', // "1234"
        maxLength: 4
    });

    const params = {
        headers: { 'Content-Type': 'application/json' },
        timeout: '10s', // Monitor for 5-10s delays as requested
    };

    const res = http.post(url, payload, params);
    
    // Explicit metric logging for real-time parsing
    console.log(`METRIC: LATENCY=${res.timings.duration} STATUS=${res.status} ERR=${res.error || 'none'}`);

    // Detailed checks for degradation analysis
    check(res, {
        'status is 200': (r) => r.status === 200,
        'connection refused': (r) => r.status === 0 || r.error_code === 1000, 
        'request id returned': (r) => r.json() && r.json().requestId !== undefined,
        'fast response (<1s)': (r) => r.timings.duration < 1000,
    });

    // Minimal sleep to maintain high RPS
    sleep(0.01); 
}
