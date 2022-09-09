package com.bettersms.bulksmsV2.repository;


import com.bettersms.bulksmsV2.model.BulkOutbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OutboxRepository extends JpaRepository<BulkOutbox, Long> {

}
