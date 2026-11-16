package com.arktech.superaccountant.filedreturns;

import java.util.UUID;

public record FiledReturnImportedEvent(UUID organizationId, UUID snapshotId) {
}
