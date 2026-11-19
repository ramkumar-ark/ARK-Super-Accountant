package com.arktech.superaccountant.gstr1.repository;

import com.arktech.superaccountant.gstr1.models.PreparedReturn;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PreparedReturnRepository extends JpaRepository<PreparedReturn, UUID> {

    Optional<PreparedReturn> findByWorkflowIdAndReturnKind(UUID workflowId, String returnKind);
}
