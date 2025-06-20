package com.practice1.backend.account.dto.response;

import com.practice1.backend.account.entity.AccountEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class AccountResDto {
    private Long accountId;
    private Long balance;

    public static AccountResDto fromEntity(AccountEntity entity){
        return AccountResDto.builder()
                .accountId(entity.getAccountId())
                .balance(entity.getBalance())
                .build();
    }
}
