package com.arktech.superaccountant.gstr1.payload.request;

public class PortalUploadRequest {

    private String fileHash;

    private String note;

    public String getFileHash() {
        return fileHash;
    }

    public void setFileHash(String fileHash) {
        this.fileHash = fileHash;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
