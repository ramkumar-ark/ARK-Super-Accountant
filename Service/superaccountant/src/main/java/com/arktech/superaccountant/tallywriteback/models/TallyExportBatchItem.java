package com.arktech.superaccountant.tallywriteback.models;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "tally_export_batch_items")
@Data
@NoArgsConstructor
public class TallyExportBatchItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "batch_id", nullable = false)
    private UUID batchId;

    @Column(name = "item_id", nullable = false)
    private UUID itemId;

    @Column(name = "name_sent")
    private String nameSent;

    @Convert(converter = ExistenceOutcomeConverter.class)
    @Column(name = "check_outcome", length = 20)
    private ExistenceOutcome checkOutcome;
}
