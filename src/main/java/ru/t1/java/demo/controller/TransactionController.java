package ru.t1.java.demo.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.t1.java.demo.aop.HandlingResult;
import ru.t1.java.demo.aop.LogException;
import ru.t1.java.demo.aop.Metric;
import ru.t1.java.demo.model.Transaction;
import ru.t1.java.demo.model.dto.TransactionDto;
import ru.t1.java.demo.service.TransactionService;
import ru.t1.java.demo.util.TransactionMapper;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Slf4j
public class TransactionController {

    private final TransactionService transactionService;

    @LogException
    @Metric(maxExecutionTime = 1)
    @HandlingResult
    @PostMapping("transaction/register")
    public ResponseEntity<TransactionDto> register(@RequestBody TransactionDto dto) {
        log.info("Registering transaction: {}", dto);
        TransactionDto transactionDto = transactionService.registerTransaction(TransactionMapper.toEntityWithId(dto));
        return ResponseEntity.ok().body(transactionDto);
    }

    @LogException
    @Metric(maxExecutionTime = 1)
    @PatchMapping("/transaction/{transactionId}")
    @HandlingResult
    public TransactionDto patchById(@PathVariable Long transactionId,
                                    @RequestBody TransactionDto dto) {

        return transactionService.patchById(transactionId, dto);
    }

    @LogException
    @Metric(maxExecutionTime = 1)
    @GetMapping(value = "/transactions/{accountId}")
    @HandlingResult
    public List<TransactionDto> getAllByAccountId(@PathVariable Long accountId) {

        return transactionService.getAllAccountById(accountId);
    }

    @LogException
    @Metric(maxExecutionTime = 1)
    @GetMapping(value = "/transaction/{transactionId}")
    @HandlingResult
    public TransactionDto getById(@PathVariable Long transactionId) {

        return transactionService.getById(transactionId);
    }

    @LogException
    @Metric(maxExecutionTime = 1)
    @DeleteMapping("/transaction/{transactionId}")
    @HandlingResult
    public void deleteById(@PathVariable Long transactionId) {

        transactionService.deleteById(transactionId);
    }

}
