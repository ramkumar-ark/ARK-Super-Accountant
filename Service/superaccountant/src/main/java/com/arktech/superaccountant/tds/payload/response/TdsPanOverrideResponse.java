package com.arktech.superaccountant.tds.payload.response;

import java.time.Instant;

public record TdsPanOverrideResponse(String partyLedgerName, String pan, String approvedBy,
                                     Instant approvedAt, String mastersPan,
                                     boolean mastersDiffers) {
}
