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
import com.practice1.backend.account_auth.service.AccountAuthService;
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
    private final AccountAuthService accountAuthService;

    @Transactional(readOnly = true)
    public List<AccountResDto> getAccountList(Long id) {
        return accountJpaRepository.findByCustomer_CustomerId(id).stream()
                .map(AccountResDto::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public Long getBalance(Long accountId) {
        return accountJpaRepository.findBalanceByAccountId(accountId);
    }

    @Transactional
    public void withdrawAndDeposit(WithdrawReqDto req) throws AuthException {
        AccountEntity from = accountJpaRepository.findByIdForUpdate(req.getFromAccountId())
                .orElseThrow(() -> new AccountNotFoundException(req.getFromAccountId()));
        accountAuthService.verifyPassword(req.getFromAccountId(), req.getRawPassword());
        AccountEntity to = accountJpaRepository.findByIdForUpdate(req.getToAccountId())
                .orElseThrow(() -> new AccountNotFoundException(req.getToAccountId()));
        from.withdraw(req.getAmount());
        to.deposit(req.getAmount());
    }

}
