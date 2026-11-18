package com.arktech.superaccountant.gstr1.repository;

import com.arktech.superaccountant.gstr1.models.Gstr1FillIn;
import com.arktech.superaccountant.gstr1.models.Gstr1FillInAttribute;
import com.arktech.superaccountant.gstr1.models.Gstr1FillInScope;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface Gstr1FillInRepository extends JpaRepository<Gstr1FillIn, UUID> {

    Optional<Gstr1FillIn> findByWorkflowIdAndScopeAndKeyAndAttribute(
            UUID workflowId, Gstr1FillInScope scope, String key, Gstr1FillInAttribute attribute);

    List<Gstr1FillIn> findByWorkflowId(UUID workflowId);

    List<Gstr1FillIn> findByWorkflowIdAndScope(UUID workflowId, Gstr1FillInScope scope);
}
