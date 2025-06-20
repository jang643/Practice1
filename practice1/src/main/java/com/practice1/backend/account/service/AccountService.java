package com.practice1.backend.account.service;

import com.practice1.backend.account.dto.request.WithdrawReqDto;
import com.practice1.backend.account.dto.response.AccountResDto;
import com.practice1.backend.account.entity.AccountEntity;
import com.practice1.backend.account.exception.AccountNotAvailableException;
import com.practice1.backend.account.exception.AccountNotFoundException;
import com.practice1.backend.account.repository.AccountJpaRepository;
import com.practice1.backend.account_auth.entity.AccountAuthEntity;
import com.practice1.backend.account_auth.exception.AuthException;
import com.practice1.backend.account_auth.repository.AccountAuthJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;

import javax.persistence.LockTimeoutException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AccountService {
    private final AccountJpaRepository accountJpaRepository;
    private final AccountAuthJpaRepository accountAuthJpaRepository;
    private final PasswordEncoder encoder;

    @Transactional(readOnly = true)
    public List<AccountResDto> getAccountList(Long id) {
        return accountJpaRepository.findByCustomer_CustomerId(id).stream()
                .map(AccountResDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Long getBalance(Long accountId) {
        return accountJpaRepository.findBalanceByAccountId(accountId);
    }

    @Retryable(retryFor= {PessimisticLockingFailureException.class, LockTimeoutException.class},
            backoff = @Backoff(delay = 100, multiplier = 2))
    @Transactional
    public void withdrawAndDeposit(WithdrawReqDto req) throws AuthException {
        AccountEntity from = accountJpaRepository.findByIdOptimistic(req.getFromAccountId())
                .orElseThrow(() -> new AccountNotFoundException(req.getFromAccountId()));
        verifyPassword(req.getFromAccountId(), req.getRawPassword());
        AccountEntity to = accountJpaRepository.findByIdOptimistic(req.getToAccountId())
                .orElseThrow(() -> new AccountNotFoundException(req.getToAccountId()));
        from.withdraw(req.getAmount());
        to.deposit(req.getAmount());
    }

    @Transactional(noRollbackFor = AuthException.class)
    public void verifyPassword(Long accountId, String rawPassword) {
        AccountAuthEntity auth = accountAuthJpaRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));

        if(!encoder.matches(rawPassword, auth.getPassword())) {
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
        if(auth.getStatus().equals("LOCKED") && lockUntil.isAfter(LocalDateTime.now())){
            throw new AccountNotAvailableException();
        } else {
            auth.unlock();
        }
    }
}
