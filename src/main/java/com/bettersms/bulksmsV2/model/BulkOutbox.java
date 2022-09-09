package com.bettersms.bulksmsV2.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "BULK_OUTBOX")
@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class BulkOutbox {

    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    private Long id;

    private String message;

    private String correlator;

    private String msisdn;

    private String senderId;

    private String response;

    private String resultUrl;

    private LocalDateTime createdAt;


}
