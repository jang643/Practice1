package com.practice1.backend.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.practice1.backend.account.dto.request.WithdrawReqDto;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class IdempotencyTest {

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
    @DisplayName("Test 1. 서로 다른 요청 2개 → 모두 성공")
    void different_requests_should_all_succeed() throws Exception {
        for (int i = 0; i < 2; i++) {
            WithdrawReqDto req = WithdrawReqDto.builder()
                    .fromAccountId(1L)
                    .toAccountId(2L)
                    .amount(1000L + i * 100) // 서로 다른 요청
                    .rawPassword("123456")
                    .build();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Idempotency-Key", UUID.randomUUID().toString());

            HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(req), headers);
            ResponseEntity<Void> response = new RestTemplate().exchange(
                    baseUrl, HttpMethod.POST, entity, Void.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        }
    }

    @Test
    @DisplayName("Test 2. 동일 요청 3회 → 첫 요청만 처리, 나머지는 캐시 응답")
    void same_request_3_times_should_return_cached_response() throws Exception {
        String idemKey = UUID.randomUUID().toString();

        WithdrawReqDto req = WithdrawReqDto.builder()
                .fromAccountId(1L)
                .toAccountId(2L)
                .amount(1000L)
                .rawPassword("123456")
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", idemKey);

        HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(req), headers);
        RestTemplate restTemplate = new RestTemplate();

        // 1. 첫 요청
        ResponseEntity<Void> response1 = restTemplate.exchange(baseUrl, HttpMethod.POST, entity, Void.class);
        assertThat(response1.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // 2~3. 동일 키, 동일 요청 반복
        for (int i = 0; i < 2; i++) {
            ResponseEntity<Void> cached = restTemplate.exchange(baseUrl, HttpMethod.POST, entity, Void.class);
            assertThat(cached.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT); // AOP에서는 캐시 응답 반환
        }
    }

    @Test
    @DisplayName("Test 3. 처리 중 동일 요청 들어오면 409 Conflict")
    void same_request_while_processing_should_return_409() throws Exception {
        String idemKey = UUID.randomUUID().toString();

        WithdrawReqDto req = WithdrawReqDto.builder()
                .fromAccountId(1L)
                .toAccountId(2L)
                .amount(1000L)
                .rawPassword("123456")
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", idemKey);

        HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(req), headers);

        // 1. 첫 요청은 처리 지연을 유도 (비동기로 처리)
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(() -> {
            try {
                new RestTemplate().exchange(baseUrl, HttpMethod.POST, entity, Void.class);
            } catch (Exception ignored) {
            }
        });

        // 2. 바로 동일 요청 재시도 (처리 중이므로 409 기대)
        Thread.sleep(100); // 약간의 시간 간격
        ResponseEntity<String> second = new RestTemplate().exchange(baseUrl, HttpMethod.POST, entity, String.class);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }


}