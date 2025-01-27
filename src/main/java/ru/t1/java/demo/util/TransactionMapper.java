package ru.t1.java.demo.util;

import org.springframework.stereotype.Component;
import ru.t1.java.demo.enums.AccountType;
import ru.t1.java.demo.model.Account;
import ru.t1.java.demo.model.dto.AccountDto;
import ru.t1.java.demo.model.dto.TransactionDto;
import ru.t1.java.demo.model.Transaction;

@Component
public class TransactionMapper {

    public static Transaction toEntity(TransactionDto dto) {
        return Transaction.builder()
                .accountId(dto.getAccountId())
                .amount(dto.getAmount())
                .timestamp(dto.getTimestamp())
                .build();
    }

    public static TransactionDto toDto(Transaction entity) {
        return TransactionDto.builder()
                .accountId(entity.getAccountId())
                .amount(entity.getAmount())
                .timestamp(entity.getTimestamp())
                .build();
    }

    public static Transaction toEntityWithId(TransactionDto dto) {
//        if (dto.getMiddleName() == null) {
//            throw new NullPointerException();
//        }
        int randomInt = (int)(Math.random() * 100000000);
        return Transaction.builder()
                .transactionId(randomInt)
                .accountId(dto.getAccountId())
                .amount(dto.getAmount())
                .timestamp(dto.getTimestamp())
                .build();
    }

}
