package com.arktech.superaccountant.filedreturns;

public enum Gstr3bRowCode {
    R3_1A("3.1a"),
    R3_1B("3.1b"),
    R3_1C("3.1c"),
    R3_1D("3.1d"),
    R3_1E("3.1e"),
    R4A1("4A1"),
    R4A2("4A2"),
    R4A3("4A3"),
    R4A4("4A4"),
    R4A5("4A5"),
    R4B1("4B1"),
    R4B2("4B2"),
    R4C("4C"),
    R4D1("4D1"),
    R4D2("4D2"),
    R5("5"),
    R6_1_CASH("6.1-CASH"),
    R6_1_ITC("6.1-ITC");

    private final String code;

    Gstr3bRowCode(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
