package com.practice1.backend.account.service.lock;

import com.practice1.backend.account.dto.request.WithdrawReqDto;
import com.practice1.backend.account.exception.AccountLockTimeoutException;
import com.practice1.backend.account.service.AccountService;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import javax.persistence.LockTimeoutException;
import javax.transaction.Transactional;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class GlobalAccountLockFacade {
    private final RedissonClient redisson;
    private final AccountService accountService;

    @Retryable(
            retryFor = {LockTimeoutException.class, PessimisticLockingFailureException.class},
            backoff = @Backoff(delay = 100, multiplier = 2)
    )
    @Transactional
    public void transferWithGlobalLock(WithdrawReqDto req) throws AccountLockTimeoutException, InterruptedException {
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
}


