package com.practice1.backend.concurrency;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.practice1.backend.account.dto.request.WithdrawReqDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;
import java.util.concurrent.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class TransferControllerConcurrencyTest {

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    private String baseUrl;

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port + "/account/transfer";
    }

    @Test
    void 잔액초과_송금_충돌_테스트() throws InterruptedException {
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    WithdrawReqDto req = WithdrawReqDto.builder()
                            .fromAccountId(1L)
                            .toAccountId(2L)
                            .amount(1000000L)
                            .rawPassword("123456")
                            .build();

                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.APPLICATION_JSON);
                    headers.set("Idempotency-Key", UUID.randomUUID().toString());

                    HttpEntity<String> entity = new HttpEntity<>(
                            objectMapper.writeValueAsString(req), headers
                    );

                    RestTemplate restTemplate = new RestTemplate();
                    ResponseEntity<Void> response = restTemplate.exchange(
                            baseUrl,
                            HttpMethod.POST,
                            entity,
                            Void.class
                    );

                    System.out.println("✅ 응답 코드: " + response.getStatusCode());
                } catch (Exception e) {
                    System.out.println("❌ 예외 발생: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();

        // 여기서 DB 잔액 조회 및 검증 로직을 추가하면 더 정확한 테스트가 됩니다.
    }
}