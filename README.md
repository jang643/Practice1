# 은행 송금 트랜잭션 구현 프로젝트 1

## 📌 프로젝트 소개
실제 은행 시스템을 모사한 송금 로직을 Spring Boot 기반 구현. 
실행 과정은 계좌 인증 → 출금/입금 처리 → 트랜잭션 관리 → 동시성 제어

## 🧩 주요 기능

| 기능 | 설명 |
|------|------|
| 계좌 인증 | 출금 요청 시 비밀번호 검증을 통한 인증 수행 |
| 송금 처리 | 송금자 계좌 출금 및 수신자 계좌 입금 처리 |
| 예외 처리 | 계좌 없음, 잔액 부족, 비밀번호 오류 등 세분화된 예외 정의 |
| 트랜잭션 처리 | Spring `@Transactional` 을 통한 출금-입금 원자성 보장 |
| 비관적 락 처리 | `@Lock(LockModeType.PESSIMISTIC)` 기반 계좌 동시성 제어 |
| Redis 분산 락 처리 | Redisson을 활용한 분산 환경 대응 |

## 🏗️ 프로젝트 구조

```
📁 src
├── 📁 main
│   ├── 📁 java
│   │   └── 📁 com.practice1.backend
│   │       ├── 📁 account
│   │       │   ├── 📁 controller      
│   │       │   ├── 📁 dto               
│   │       │   ├── 📁 entity             
│   │       │   ├── 📁 exception         
│   │       │   ├── 📁 repository        
│   │       │   ├── 📁 service          
│   │       │   └── 📁 service/lock    
│   │       ├── 📁 account_auth           
│   │       │   ├── 📁 exception
│   │       │   ├── 📁 entity
│   │       │   ├── 📁 repository
│   │       │   └── 📁 service
│   │       ├── 📁 web
│   │       │   └── 📁 filter             
│   │       └── 📁 config
```

## 🔐 동시성 처리 및 트랜잭션 보장 흐름

### 1. 송금 요청 처리 흐름

```http
POST /transfer
Headers:
- X-Idempotency-Key: UUID

Body:
{
  "fromAccountId": 1,
  "toAccountId": 2,
  "amount": 10000,
  "rawPassword": "1234"
}
```

### 2. 전송 처리

```java
@PostMapping("/transfer")
public ResponseEntity<?> transfer(@RequestBody @Valid WithdrawReqDto req ) throws InterruptedException {
  lockFacade.transferWithGlobalLock(req);
  return ResponseEntity.noContent().build();
}
```

### 3. Redis 기반 전역 락 처리

```java
@Retryable(
            retryFor = {LockTimeoutException.class, PessimisticLockingFailureException.class},
            backoff = @Backoff(delay = 100, multiplier = 2)
    )
@Transactional
public void transferWithGlobalLock(WithdrawReqDto req) throws AuthException, InterruptedException {
  RLock lock = redisson.getLock("account:" + req.getFromAccountId());
  boolean locked = false;
  try {
    locked = lock.tryLock(3, 10, TimeUnit.SECONDS);
    if (!locked) throw new LockTimeoutException();
    accountService.withdrawAndDeposit(req);
  } finally {
    if (locked && lock.isHeldByCurrentThread()) lock.unlock();
  }
}
```

### 4. 비관적 락 기반 송금 처리

```java
@Transactional
public void withdrawAndDeposit(WithdrawReqDto req) throws AuthException {
  AccountEntity from = accountJpaRepository.findByIdForUpdate(req.getFromAccountId())
      .orElseThrow(() -> new AccountNotFoundException(req.getFromAccountId()));
  verifyPassword(req.getFromAccountId(), req.getRawPassword());
  AccountEntity to = accountJpaRepository.findByIdForUpdate(req.getToAccountId())
      .orElseThrow(() -> new AccountNotFoundException(req.getToAccountId()));
  from.withdraw(req.getAmount());
  to.deposit(req.getAmount());
}
```

### 5. 계좌 비밀번호 인증 및 잠금 해제 처리

```java
@Transactional(noRollbackFor = AuthException.class)
public void verifyPassword(Long accountId, String rawPassword) {
    AccountAuthEntity auth = accountAuthJpaRepository.findById(accountId)
        .orElseThrow(() -> new AccountNotFoundException(accountId));

    if (!encoder.matches(rawPassword, auth.getPassword())) {
        auth.increaseFail();
        throw new AuthException(auth.getFailCount());
    } else {
        unlock(accountId);
    }
}

@Transactional
public void unlock(Long accountId) {
    AccountAuthEntity auth = accountAuthJpaRepository.findById(accountId)
        .orElseThrow(() -> new AccountNotFoundException(accountId));
    LocalDateTime lockUntil = auth.getLockUntil();
    if (auth.getStatus().equals("LOCKED") && lockUntil.isAfter(LocalDateTime.now())) {
        throw new AccountNotAvailableException();
    } else {
        auth.unlock();
    }
}
```

### 6. 낙관적 락 쿼리 설정

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select a from AccountEntity a where a.accountId = :accountId")
Optional<AccountEntity> findByIdForUpdate(Long accountId);
```

### 7. 멱등성 필터 적용 (Idempotency)

```java
@Override
protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws IOException, ServletException {
    if ("POST".equals(req.getMethod()) && req.getRequestURI().equals("/transfer")) {
        String key = req.getHeader("X-Idempotency-Key");
        if (key == null) {
            res.sendError(400, "Missing idempotency key");
            return;
        }
        Boolean ok = redis.opsForValue()
                .setIfAbsent("idem:" + key, "1", Duration.ofMinutes(5));
        if (Boolean.FALSE.equals(ok)) {
            res.sendError(409, "Duplicate request");
            return;
        }
    }
    chain.doFilter(req, res);
}
```

---

## ⚙️ 기술 스택

- Java 17
- Spring Boot 2.7.x
- Spring Data JPA
- MySQL 8.x
- Redis (Redisson)
- Gradle
