package com.arktech.superaccountant.daybook;

import com.arktech.superaccountant.daybook.models.DayBookUploadJob;
import com.arktech.superaccountant.daybook.repository.DayBookUploadJobRepository;
import com.arktech.superaccountant.daybook.repository.ParsedVoucherRepository;
import com.arktech.superaccountant.daybook.services.DayBookIntakeResult;
import com.arktech.superaccountant.daybook.services.DayBookIntakeService;
import com.arktech.superaccountant.daybook.services.DayBookMasterValidationService;
import com.arktech.superaccountant.daybook.services.VoucherCanonicalizer;
import com.arktech.superaccountant.masters.models.GateResult;
import com.arktech.superaccountant.masters.services.MastersGateService;
import com.arktech.superaccountant.tally.models.TallyMessage;
import com.arktech.superaccountant.tally.models.Voucher;
import com.arktech.superaccountant.tally.services.TallyParserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DayBookIntakeServiceTest {

    @Mock TallyParserService tallyParserService;
    @Mock com.arktech.superaccountant.daybook.services.DayBookParserService dayBookParserService;
    @Mock DayBookUploadJobRepository dayBookUploadJobRepository;
    @Mock ParsedVoucherRepository parsedVoucherRepository;
    @Mock DayBookMasterValidationService dayBookMasterValidationService;
    @Mock MastersGateService mastersGateService;

    @InjectMocks
    DayBookIntakeService intakeService;

    private Voucher voucher(String date) {
        Voucher v = new Voucher();
        v.setVouchertypename("Purchase");
        v.setDate(date);
        v.setAllLedgerEntries(List.of());
        v.setLedgerEntries(List.of());
        return v;
    }

    @Test
    void intake_nullDateVoucher_ignoredForCoverage() throws java.io.IOException {
        when(mastersGateService.checkGate(any(UUID.class))).thenReturn(GateResult.open());
        TallyMessage tallyMessage = new TallyMessage();
        tallyMessage.setTallymessage(List.of(voucher(null), voucher("20250401")));
        when(tallyParserService.parseJson(any())).thenReturn(tallyMessage);
        when(dayBookUploadJobRepository.save(any(DayBookUploadJob.class)))
                .thenAnswer(i -> i.getArgument(0));
        when(dayBookParserService.persistVouchers(any(), any(), any())).thenReturn(List.of());

        DayBookIntakeResult result = intakeService.intake(
                new MockMultipartFile("file", "daybook.json", "application/json", "{}".getBytes()),
                LocalDate.of(2025, 4, 1), LocalDate.of(2025, 4, 30),
                UUID.randomUUID(), "op1", null);

        assertThat(result.warnings()).isEmpty();
        assertThat(result.job().getStatus()).isEqualTo(com.arktech.superaccountant.masters.models.UploadJobStatus.COMPLETED);
        assertThat(result.job().getMinVoucherDate()).isEqualTo(LocalDate.of(2025, 4, 1));
        assertThat(result.job().getMaxVoucherDate()).isEqualTo(LocalDate.of(2025, 4, 1));
        // Null-date voucher must not count as outside coverage
        assertThat(result.job().getErrorMessage()).isNull();
    }
}
