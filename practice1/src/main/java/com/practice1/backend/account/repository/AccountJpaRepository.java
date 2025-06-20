package com.practice1.backend.account.repository;

import com.practice1.backend.account.entity.AccountEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import javax.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface AccountJpaRepository extends JpaRepository<AccountEntity, Long> {
    List<AccountEntity> findByCustomer_CustomerId(Long customerId);

    @Query("SELECT a.balance FROM AccountEntity a WHERE  a.accountId = :accountId")
    Long findBalanceByAccountId(@Param("accountId") Long accountId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AccountEntity a where a.accountId = :accountId")
    Optional<AccountEntity> findByIdForUpdate(Long accountId);

    @Lock(LockModeType.OPTIMISTIC)
    @Query("select a from AccountEntity a where a.accountId = :id")
    Optional<AccountEntity> findByIdOptimistic(Long id);
}