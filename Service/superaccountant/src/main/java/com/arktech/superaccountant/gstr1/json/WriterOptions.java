package com.arktech.superaccountant.gstr1.json;

/**
 * Writer options. {@code includeIrpTables=false} drops b2b, cdnr and exp from the
 * portal file when the filer reports through e-invoice IRP (plan Q6 B).
 */
public record WriterOptions(boolean includeIrpTables) {

    public static WriterOptions defaults() {
        return new WriterOptions(true);
    }
}
