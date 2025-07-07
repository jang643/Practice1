package com.practice1.backend.account.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

@Getter
@Builder
@ToString
@AllArgsConstructor
public class WithdrawReqDto {

    @NotNull private Long fromAccountId;
    @NotNull private Long toAccountId;

    @Min(1)
    private Long amount;
    @Size(min = 6, max = 6)
    private String rawPassword;
}