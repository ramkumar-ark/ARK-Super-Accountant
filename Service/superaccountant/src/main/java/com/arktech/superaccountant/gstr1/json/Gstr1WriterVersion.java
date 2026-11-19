package com.arktech.superaccountant.gstr1.json;

/**
 * Pinned GSTR-1 portal file constants, verified against the GST Offline Tool V3.2.4
 * output (scout report sa-gstr1-json-ref, evidence grade A).
 */
public final class Gstr1WriterVersion {

    /** Portal JSON {@code version} value written by the offline tool. */
    public static final String PORTAL_VERSION = "GST3.2.4";

    /** Portal JSON {@code hash} placeholder written by the offline tool. */
    public static final String HASH_PLACEHOLDER = "hash";

    /** Maximum item entries per file part (offline tool readme). */
    public static final int MAX_ITEMS_PER_PART = 19_000;

    /** Maximum serialized size per file part; the offline tool splits at 4.7 MB. */
    public static final long MAX_BYTES_PER_PART = (long) (4.7 * 1024 * 1024);

    private Gstr1WriterVersion() {
    }
}
