package com.practice1.backend.account_auth.service;

import com.practice1.backend.account.exception.AccountNotAvailableException;
import com.practice1.backend.account.exception.AccountNotFoundException;
import com.practice1.backend.account_auth.entity.AccountAuthEntity;
import com.practice1.backend.account_auth.exception.AuthException;
import com.practice1.backend.account_auth.repository.AccountAuthJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.springframework.transaction.annotation.Propagation.REQUIRES_NEW;

@Service
@RequiredArgsConstructor
public class AccountAuthService {

    private final AccountAuthJpaRepository accountAuthJpaRepository;
    private final PasswordEncoder encoder;

    @Transactional(propagation = REQUIRES_NEW, noRollbackFor = AuthException.class)
    public void verifyPassword(Long accountId, String rawPassword) {
        AccountAuthEntity auth = accountAuthJpaRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));

        if (!encoder.matches(rawPassword, auth.getPassword())) {
            auth.increaseFail();
            throw new AuthException(auth.getFailCount());
        }

        LocalDateTime lockUntil = auth.getLockUntil();
        if (auth.getStatus().equals("LOCKED") && lockUntil != null && lockUntil.isAfter(LocalDateTime.now())) {
            throw new AccountNotAvailableException();
        } else {
            auth.unlock();
        }
    }
}