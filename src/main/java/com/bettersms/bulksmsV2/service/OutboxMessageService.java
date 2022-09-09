package com.bettersms.bulksmsV2.service;

import com.bettersms.bulksmsV2.model.BulkOutbox;
import com.bettersms.bulksmsV2.repository.OutboxRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class OutboxMessageService {


    private OutboxRepository outboxRepository;

    public OutboxMessageService(OutboxRepository outboxRepository) {
        this.outboxRepository = outboxRepository;
    }


    public void  saveSentMessage(final BulkOutbox bulkOutbox) {

        try {
             BulkOutbox result = outboxRepository.save(bulkOutbox);

        } catch(Exception jdbcExec){
            throw new RuntimeException("Error saving record to database",new RuntimeException(jdbcExec.getMessage()));
        }
    }
}
