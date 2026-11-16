package com.arktech.superaccountant.filedreturns;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FiledReturnLineRepository extends JpaRepository<FiledReturnLine, UUID> {
    List<FiledReturnLine> findBySnapshotIdOrderByLineNo(UUID snapshotId);
}
