package com.practice1.backend.account.entity;

import com.practice1.backend.customer.entity.CustomerEntity;
import lombok.Getter;
import org.hibernate.annotations.CreationTimestamp;

import javax.persistence.*;
import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "account")
public class AccountEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "account_id")
    private Long accountId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false, foreignKey = @ForeignKey(name = "fk_account_customer"))
    private CustomerEntity customer;

    @Column(name = "balance", nullable = false)
    private Long balance = 0L;

    @Version
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public void withdraw(Long balance) {
        this.balance -= balance;
    }

    public void deposit(Long balance) {
        this.balance += balance;
    }
}
