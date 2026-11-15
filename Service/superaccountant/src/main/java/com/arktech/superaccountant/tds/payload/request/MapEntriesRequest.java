package com.arktech.superaccountant.tds.payload.request;

import java.util.List;
import java.util.UUID;

public record MapEntriesRequest(UUID challanId, List<UUID> entryIds) {
}
