package com.practice1.backend.concurrency;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.practice1.backend.account.dto.request.WithdrawReqDto;
import com.practice1.backend.account.repository.AccountJpaRepository;
import com.practice1.backend.account_auth.repository.AccountAuthJpaRepository;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Fail.fail;

@Slf4j
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class TransferIntegrationTest {

    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AccountJpaRepository accountJpaRepository;

    @Autowired
    private AccountAuthJpaRepository accountAuthJpaRepository;

    private String baseUrl;

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port + "/account/transfer";
    }

    @Test
    @DisplayName("Test 1. 정상 송금 요청 통합 테스트")
    void transfer_success() throws Exception {
        // given
        Long fromAccountId = 1L;
        Long toAccountId = 2L;
        Long amount = 5000L;
        String password = "123456";

        long fromBefore = accountJpaRepository.findBalanceByAccountId(fromAccountId);
        long toBefore = accountJpaRepository.findBalanceByAccountId(toAccountId);

        log.info("송금 전 잔액 - from: {}, to: {}", fromBefore, toBefore);

        WithdrawReqDto req = WithdrawReqDto.builder()
                .fromAccountId(fromAccountId)
                .toAccountId(toAccountId)
                .amount(amount)
                .rawPassword(password)
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", UUID.randomUUID().toString());

        HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(req), headers);

        // when
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<Void> response = restTemplate.exchange(
                baseUrl,
                HttpMethod.POST,
                entity,
                Void.class
        );

        log.info("응답 상태: {}", response.getStatusCode());

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        long fromAfter = accountJpaRepository.findBalanceByAccountId(fromAccountId);
        long toAfter = accountJpaRepository.findBalanceByAccountId(toAccountId);

        log.info("송금 후 잔액 - from: {}, to: {}", fromAfter, toAfter);

        assertThat(fromAfter).isEqualTo(fromBefore - amount);
        assertThat(toAfter).isEqualTo(toBefore + amount);
    }

    @Test
    @DisplayName("Test 2. 비밀번호 오류 6회 → 이후 정상 입력 시 계좌 잠김 확인")
    void password_fail_then_lock_verification_test() throws Exception {
        Long fromAccountId = 1L;
        Long toAccountId = 2L;
        Long amount = 1000L;
        String wrongPassword = "093123";
        String correctPassword = "123456";

        RestTemplate restTemplate = new RestTemplate();

        // 1. 잘못된 비밀번호 6회 입력
        for (int i = 1; i <= 5; i++) {
            WithdrawReqDto req = WithdrawReqDto.builder()
                    .fromAccountId(fromAccountId)
                    .toAccountId(toAccountId)
                    .amount(amount)
                    .rawPassword(wrongPassword)
                    .build();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Idempotency-Key", UUID.randomUUID().toString());

            HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(req), headers);

            try {
                ResponseEntity<String> response = restTemplate.exchange(
                        baseUrl,
                        HttpMethod.POST,
                        entity,
                        String.class
                );
                log.warn("[{}회차] 응답 상태: {}, 메시지: {}", i, response.getStatusCode(), response.getBody());
            } catch (Exception e) {
                log.warn("[{}회차] 예외 발생:", i);
            }
        }

        // 2. 계좌가 잠겼는지 DB에서 상태 조회
        var auth = accountAuthJpaRepository.findById(fromAccountId).orElseThrow();
        log.info("최종 상태: status={}, failCount={}, lockUntil={}",
                auth.getStatus(), auth.getFailCount(), auth.getLockUntil());

        assertThat(auth.getStatus()).isEqualTo("LOCKED");
        assertThat(auth.getFailCount()).isGreaterThanOrEqualTo(6);
        assertThat(auth.getLockUntil()).isNotNull();

        // 3. 이후 정상 비밀번호로 재시도했을 때도 잠김 유지되는지 확인
        WithdrawReqDto validReq = WithdrawReqDto.builder()
                .fromAccountId(fromAccountId)
                .toAccountId(toAccountId)
                .amount(amount)
                .rawPassword(correctPassword)
                .build();

        HttpHeaders validHeaders = new HttpHeaders();
        validHeaders.setContentType(MediaType.APPLICATION_JSON);
        validHeaders.set("Idempotency-Key", UUID.randomUUID().toString());

        HttpEntity<String> validEntity = new HttpEntity<>(objectMapper.writeValueAsString(validReq), validHeaders);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    baseUrl,
                    HttpMethod.POST,
                    validEntity,
                    String.class
            );
            log.error("잠긴 계좌로 정상 비밀번호 요청했지만 응답이 돌아옴! 상태: {}", response.getStatusCode());
            fail("잠긴 계좌인데 송금이 성공해서는 안 됨");
        } catch (Exception e) {
            log.info("잠긴 계좌로 정상 송금 요청 시 예외 발생 확인 완료: {}", e.getMessage());
        }
    }

    @Test
    @DisplayName("Test 3. 동시에 여러 송금 요청 시 트랜잭션 정합성 보장 테스트")
    void concurrent_transfer_test() throws Exception {
        // given
        Long fromAccountId = 1L;
        Long toAccountId = 2L;
        Long amount = 1000L;
        int threadCount = 10;

        long fromBefore = accountJpaRepository.findBalanceByAccountId(fromAccountId);
        long toBefore = accountJpaRepository.findBalanceByAccountId(toAccountId);
        log.info("송금 전 잔액 - from: {}, to: {}", fromBefore, toBefore);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    WithdrawReqDto req = WithdrawReqDto.builder()
                            .fromAccountId(fromAccountId)
                            .toAccountId(toAccountId)
                            .amount(amount)
                            .rawPassword("123456")
                            .build();

                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.APPLICATION_JSON);
                    headers.set("Idempotency-Key", UUID.randomUUID().toString());

                    HttpEntity<String> entity = new HttpEntity<>(
                            objectMapper.writeValueAsString(req), headers);

                    RestTemplate restTemplate = new RestTemplate();
                    ResponseEntity<Void> response = restTemplate.exchange(
                            baseUrl,
                            HttpMethod.POST,
                            entity,
                            Void.class
                    );

                    if (response.getStatusCode().is2xxSuccessful()) {
                        successCount.incrementAndGet();
                        log.info("성공 응답: {}", response.getStatusCode());
                    } else {
                        failCount.incrementAndGet();
                        log.warn("실패 응답: {}", response.getStatusCode());
                    }

                } catch (Exception e) {
                    log.error("예외 발생: {}", e.getMessage());
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();

        long fromAfter = accountJpaRepository.findBalanceByAccountId(fromAccountId);
        long toAfter = accountJpaRepository.findBalanceByAccountId(toAccountId);

        log.info("송금 후 잔액 - from: {}, to: {}", fromAfter, toAfter);
        log.info("성공 수: {}, 실패 수: {}", successCount.get(), failCount.get());

        // then
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(fromAfter).isEqualTo(fromBefore - amount);
        assertThat(toAfter).isEqualTo(toBefore + amount);
    }

    @Test
    @DisplayName("Test 4. 서로 다른 계좌로 동시에 송금 요청 테스트")
    void multi_receiver_concurrent_transfer_test() throws Exception {
        Long fromAccountId = 1L;
        Long[] toAccounts = {2L, 3L, 4L, 5L, 6L};
        Long amount = 1000L;

        long fromBefore = accountJpaRepository.findBalanceByAccountId(fromAccountId);
        Map<Long, Long> toBeforeMap = Arrays.stream(toAccounts)
                .collect(Collectors.toMap(a -> a, a -> accountJpaRepository.findBalanceByAccountId(a)));

        log.info("송금 전 from 잔액: {}", fromBefore);
        toBeforeMap.forEach((id, bal) -> log.info("송금 전 to[{}] 잔액: {}", id, bal));

        ExecutorService executor = Executors.newFixedThreadPool(toAccounts.length);
        CountDownLatch latch = new CountDownLatch(toAccounts.length);

        for (Long toId : toAccounts) {
            executor.submit(() -> {
                try {
                    WithdrawReqDto req = WithdrawReqDto.builder()
                            .fromAccountId(fromAccountId)
                            .toAccountId(toId)
                            .amount(amount)
                            .rawPassword("123456")
                            .build();

                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.APPLICATION_JSON);
                    headers.set("Idempotency-Key", UUID.randomUUID().toString());

                    HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(req), headers);
                    RestTemplate restTemplate = new RestTemplate();

                    ResponseEntity<Void> response = restTemplate.exchange(
                            baseUrl,
                            HttpMethod.POST,
                            entity,
                            Void.class
                    );

                    log.info("toId={} 응답 코드: {}", toId, response.getStatusCode());

                } catch (Exception e) {
                    log.error("toId={} 예외 발생: {}", toId, e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();

        long fromAfter = accountJpaRepository.findBalanceByAccountId(fromAccountId);
        Map<Long, Long> toAfterMap = Arrays.stream(toAccounts)
                .collect(Collectors.toMap(a -> a, a -> accountJpaRepository.findBalanceByAccountId(a)));

        log.info("송금 후 from 잔액: {}", fromAfter);
        toAfterMap.forEach((id, bal) -> log.info("송금 후 to[{}] 잔액: {}", id, bal));

        // 검증
        assertThat(fromAfter).isEqualTo(fromBefore - amount * toAccounts.length);
        for (Long toId : toAccounts) {
            long before = toBeforeMap.get(toId);
            long after = toAfterMap.get(toId);
            assertThat(after).isEqualTo(before + amount);
        }
    }

    @Test
    @DisplayName("Test 5. 서로 다른 계좌에서 한 계좌로 동시에 송금 요청 테스트")
    void multi_sender_concurrent_transfer_test() throws Exception {
        Long toAccountId = 1L;
        Long[] fromAccounts = {2L, 3L, 4L, 5L, 6L};
        Long amount = 1000L;

        long toBefore = accountJpaRepository.findBalanceByAccountId(toAccountId);
        Map<Long, Long> fromBeforeMap = Arrays.stream(fromAccounts)
                .collect(Collectors.toMap(a -> a, a -> accountJpaRepository.findBalanceByAccountId(a)));

        log.info("송금 전 to 잔액: {}", toBefore);
        fromBeforeMap.forEach((id, bal) -> log.info("송금 전 from[{}] 잔액: {}", id, bal));

        ExecutorService executor = Executors.newFixedThreadPool(fromAccounts.length);
        CountDownLatch latch = new CountDownLatch(fromAccounts.length);

        for (Long fromId : fromAccounts) {
            executor.submit(() -> {
                try {
                    WithdrawReqDto req = WithdrawReqDto.builder()
                            .fromAccountId(fromId)
                            .toAccountId(toAccountId)
                            .amount(amount)
                            .rawPassword("123456")
                            .build();

                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.APPLICATION_JSON);
                    headers.set("Idempotency-Key", UUID.randomUUID().toString());

                    HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(req), headers);
                    RestTemplate restTemplate = new RestTemplate();

                    ResponseEntity<Void> response = restTemplate.exchange(
                            baseUrl,
                            HttpMethod.POST,
                            entity,
                            Void.class
                    );

                    log.info("fromId={} 응답 코드: {}", fromId, response.getStatusCode());

                } catch (Exception e) {
                    log.error("fromId={} 예외 발생: {}", fromId, e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();

        long toAfter = accountJpaRepository.findBalanceByAccountId(toAccountId);
        Map<Long, Long> fromAfterMap = Arrays.stream(fromAccounts)
                .collect(Collectors.toMap(a -> a, a -> accountJpaRepository.findBalanceByAccountId(a)));

        log.info("송금 후 to 잔액: {}", toAfter);
        fromAfterMap.forEach((id, bal) -> log.info("송금 후 from[{}] 잔액: {}", id, bal));

        // 검증
        long actualSuccess = (toAfter - toBefore) / amount;
        assertThat(actualSuccess).isBetween(1L, (long) fromAccounts.length);

        for (Long fromId : fromAccounts) {
            long before = fromBeforeMap.get(fromId);
            long after = fromAfterMap.get(fromId);
            if (before - after == amount) {
                assertThat(after).isEqualTo(before - amount);
            } else {
                log.warn("fromId={} 송금 실패로 잔액 변화 없음", fromId);
                assertThat(after).isEqualTo(before);
            }
        }
    }



}
