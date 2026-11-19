package com.arktech.superaccountant.gstr1.json;

/**
 * One self-contained GSTR-1 portal JSON part: header plus the sections assigned to it.
 */
public record PortalFilePart(String fileName, byte[] bytes, int itemCount) {
}
