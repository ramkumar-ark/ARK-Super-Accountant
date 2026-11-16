package com.arktech.superaccountant.filedreturns.recon;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * The reconcile target is not an active BASELINE row of this organization for GSTR1/GSTR3B.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class BaselineRowNotFoundException extends RuntimeException {

    public BaselineRowNotFoundException() {
        super("Baseline row not found");
    }
}
