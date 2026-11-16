package com.arktech.superaccountant.filedreturns;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FiledReturnFileRepository extends JpaRepository<FiledReturnFile, UUID> {
    List<FiledReturnFile> findAllByOrganizationId(UUID organizationId);
}
