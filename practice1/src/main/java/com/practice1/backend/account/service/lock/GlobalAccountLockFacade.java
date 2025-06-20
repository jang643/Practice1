package com.practice1.backend.account.service.lock;

import com.practice1.backend.account.dto.request.WithdrawReqDto;
import com.practice1.backend.account.service.AccountService;
import com.practice1.backend.account_auth.exception.AuthException;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import javax.persistence.LockTimeoutException;
import javax.transaction.Transactional;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class GlobalAccountLockFacade {
    private final RedissonClient redisson;
    private final AccountService accountService;

    @Transactional
    public void transferWithGlobalLock(WithdrawReqDto req) throws AuthException, InterruptedException {
        RLock lock = redisson.getLock("account:" + req.getFromAccountId());
        try {
            if (!lock.tryLock(3, 10, TimeUnit.SECONDS)) throw new LockTimeoutException();
            accountService.withdrawAndDeposit(req);
        } finally {
            if (lock.isHeldByCurrentThread()) lock.unlock();
        }
    }
}


