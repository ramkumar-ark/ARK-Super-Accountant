package com.arktech.superaccountant.workflow.snapshot;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FrozenVoucherRepository extends JpaRepository<FrozenVoucher, UUID> {

    List<FrozenVoucher> findByOrganizationId(UUID organizationId);

    List<FrozenVoucher> findByOrganizationIdAndReleasedAtIsNull(UUID organizationId);

    Optional<FrozenVoucher> findByOrganizationIdAndVoucherKeyAndReleasedAtIsNull(UUID organizationId, String voucherKey);

    List<FrozenVoucher> findByOrganizationIdAndReleasedAtIsNullAndVoucherDateAfter(UUID organizationId, LocalDate date);

    List<FrozenVoucher> findByOrganizationIdAndReleasedAtIsNullAndVoucherDateBetween(
            UUID organizationId, LocalDate from, LocalDate to);
}
