package com.arktech.superaccountant.masters.rules;

/** Single source of GST registration semantics, shared by masters and day-book rules. */
public final class GstRegistrationTypes {

    private GstRegistrationTypes() {
    }

    /** Anything but blank, Unregistered/Consumer or Unknown counts as registered. */
    public static boolean isRegistered(String registrationType) {
        if (registrationType == null || registrationType.isBlank()) {
            return false;
        }
        String r = registrationType.trim().toLowerCase();
        return !(r.contains("unregistered") || r.contains("consumer") || r.equals("unknown"));
    }
}
