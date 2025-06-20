package com.practice1.backend.account.controller;

import com.practice1.backend.account.dto.request.WithdrawReqDto;
import com.practice1.backend.account.dto.response.AccountResDto;
import com.practice1.backend.account.service.AccountService;
import com.practice1.backend.account.service.lock.GlobalAccountLockFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/account")
@RequiredArgsConstructor
public class AccountController {
    private final AccountService accountService;
    private final GlobalAccountLockFacade lockFacade;

    @GetMapping("/{id}")
    public ResponseEntity<List<AccountResDto>> getAccountList(@PathVariable Long id){
        return ResponseEntity.ok(accountService.getAccountList(id));
    }

    @GetMapping("/balance/{account_id}")
    public ResponseEntity<Long> getBalance(@PathVariable Long account_id){
        return ResponseEntity.ok(accountService.getBalance(account_id));
    }

    @PostMapping("/transfer")
    public ResponseEntity<?> transfer(@RequestBody @Valid WithdrawReqDto req,
                                      @RequestHeader(value = "X-Use-GlobalLock", defaultValue = "false") boolean useGlobal) throws InterruptedException {
        if(useGlobal) {
            lockFacade.transferWithGlobalLock(req);
        } else {
            accountService.withdrawAndDeposit(req);
        }
        return ResponseEntity.noContent().build();
    }

}
