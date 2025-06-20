package com.practice1.backend.account_auth.entity;

import com.practice1.backend.account.entity.AccountEntity;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import javax.persistence.*;
import java.time.Instant;
import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "account_auth")
public class AccountAuthEntity {
    @Id
    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", foreignKey = @ForeignKey(name = "fk_auth_account"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private AccountEntity account;

    @Column(name = "psword", nullable = false, columnDefinition = "VARCHAR(60)")
    private String password;

    @Column(name = "account_status", length = 10, nullable = false)
    private String status = "ACTIVE";

    @Column(name = "fail_count", nullable = false)
    private Integer failCount = 0;

    @Column(name = "lock_until")
    private LocalDateTime lockUntil;

    public void increaseFail() {
        this.failCount++;
        if(this.failCount >= 5) {
            this.lock();
        }
    }

    public void unlock() {
        this.status = "ACTIVE";
        this.failCount = 0;
    }

    public void lock() {
        this.lockUntil = LocalDateTime.now().plusMinutes(15);
        this.status = "LOCKED";
    }
}