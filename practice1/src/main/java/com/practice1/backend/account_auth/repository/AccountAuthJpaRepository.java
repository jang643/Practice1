package com.practice1.backend.account_auth.repository;

import com.practice1.backend.account_auth.entity.AccountAuthEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountAuthJpaRepository extends JpaRepository<AccountAuthEntity, Long> {
}
