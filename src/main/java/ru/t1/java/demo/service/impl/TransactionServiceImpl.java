package ru.t1.java.demo.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.support.SendResult;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import ru.t1.java.demo.aop.LogDataSourceError;
import ru.t1.java.demo.enums.AccountState;
import ru.t1.java.demo.enums.TransactionState;
import ru.t1.java.demo.exception.TransactionException;
import ru.t1.java.demo.kafka.KafkaProducer;
import ru.t1.java.demo.model.Account;
import ru.t1.java.demo.model.Transaction;
import ru.t1.java.demo.model.dto.TransactionRequest;
import ru.t1.java.demo.model.dto.TransactionDto;
import ru.t1.java.accept_transaction.model.dto.TransactionResponse;
import ru.t1.java.demo.repository.AccountRepository;
import ru.t1.java.demo.repository.TransactionRepository;
import ru.t1.java.demo.service.AccountService;
import ru.t1.java.demo.service.TransactionService;
import ru.t1.java.demo.util.TransactionMapper;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

@Service
@Slf4j
@RequiredArgsConstructor
public class TransactionServiceImpl implements TransactionService {
    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final AccountService accountService;
    private final KafkaProducer kafkaProducer;

    @Value("${t1.kafka.topic.transaction_accept}")
    private String topic;

    @LogDataSourceError
    @Override
    public <T> T registerTransaction(String topic, T transaction) {

        AtomicReference<T> saved = new AtomicReference<>();

        Message<T> message = MessageBuilder.withPayload(transaction)
                .setHeader(KafkaHeaders.TOPIC, topic)
                .setHeader(KafkaHeaders.KEY, UUID.randomUUID().toString())
                .build();

        CompletableFuture<SendResult<Object, Object>> future = kafkaProducer.sendMessage(message);
        future.thenAccept(sendResult -> {
            log.info("Transaction sent successfully to topic: {}", sendResult.getRecordMetadata().topic());
            ProducerRecord<Object, Object> record = sendResult.getProducerRecord();
            log.info("Message key: {}", record.key());
            log.info("Message value: {}", record.value());
            saved.set(transaction);
        }).exceptionally(ex -> {
            log.error("Failed to send transaction: {}", ex.getMessage(), ex);
            throw new RuntimeException("Failed to send transaction", ex);
        });
        future.join();
        return saved.get();
    }

    @Override
    public void acceptTransaction(List<TransactionResponse> transactions) {
        for (TransactionResponse transaction : transactions) {
            Transaction savedTransaction = transactionRepository.findByTransactionId(transaction.getTransactionId());
            Account savedAccount = accountRepository.findByAccountId(transaction.getAccountId());

            if (transaction.getState().equals(TransactionState.ACCEPTED)) {
                savedTransaction.setState(TransactionState.ACCEPTED);
                transactionRepository.save(savedTransaction);
                continue;
            }
            if (transaction.getState().equals(TransactionState.BLOCKED)) {
                savedTransaction.setState(TransactionState.BLOCKED);
                transactionRepository.save(savedTransaction);

                savedAccount.setState(AccountState.BLOCKED);
                savedAccount.setFrozenAmount(savedTransaction.getAmount());
                accountRepository.save(savedAccount);
                continue;
            }
            if (transaction.getState().equals(TransactionState.REJECTED)) {
                savedTransaction.setState(TransactionState.REJECTED);
                transactionRepository.save(savedTransaction);

                savedAccount.setBalance(savedAccount.getBalance().add(savedTransaction.getAmount()));
                accountRepository.save(savedAccount);
            }
        }
    }

    @LogDataSourceError
    @Override
    public List<Transaction> registerTransactions(List<Transaction> transactions) {

        List<Transaction> savedTransactions = new ArrayList<>();

        for (Transaction transaction : transactions) {

            TransactionRequest transactionRequest = requestTransaction(transaction);

            if (transactionRequest != null) {
                transactionRepository.save(transaction);
                registerTransaction(topic, transactionRequest);
                savedTransactions.add(transaction);
            }

        }

        return savedTransactions
                .stream()
                .sorted(Comparator.comparing(Transaction::getId))
                .toList();
    }

    private TransactionRequest requestTransaction(Transaction transaction) {
        Account account = accountService.getByAccountId(transaction.getAccountId().toString());
        if (account.getState().equals(AccountState.OPEN)) {
            transaction.setState(TransactionState.REQUESTED);
            BigDecimal balance = account.getBalance().subtract(transaction.getAmount());
            account.setBalance(balance);

            accountRepository.save(account);

            TransactionRequest transactionRequest = TransactionRequest.builder()
                    .clientId(account.getClientId())
                    .accountId(account.getAccountId())
                    .transactionId(transaction.getTransactionId())
                    .timestamp(Timestamp.from(Instant.now()))
                    .transactionAmount(transaction.getAmount())
                    .accountBalance(balance)
                    .build();
            return transactionRequest;
        }
        return null;
    }

    @LogDataSourceError
    @Override
    public Transaction patchById(String transactionId, TransactionDto dto) {
        Transaction transaction = getById(transactionId);
        transaction.setAmount(dto.getAmount());

        return transactionRepository.save(transaction);
    }

    @LogDataSourceError
    @Override
    public List<TransactionDto> getAllAccountById(String accountId) {
        List<Transaction> transactions = transactionRepository.findAllByAccountId(UUID.fromString(accountId));
        if (transactions.isEmpty()) return Collections.emptyList();

        return transactions.stream()
                .map(TransactionMapper::toDto)
                .toList();
    }

    @LogDataSourceError
    @Override
    public Transaction getById(String transactionId) {
        UUID uuid = UUID.fromString(transactionId);
        Optional<Transaction> transactionOptional = Optional.ofNullable(transactionRepository.findByTransactionId(uuid));
        if (transactionOptional.isEmpty()) throw new TransactionException("Transaction not found");
        return transactionOptional.get();
    }

    @LogDataSourceError
    @Override
    public void deleteById(String transactionId) {
        getById(transactionId);
        transactionRepository.deleteByTransactionId(UUID.fromString(transactionId));
    }

}
