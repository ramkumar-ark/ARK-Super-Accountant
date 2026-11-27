package com.arktech.superaccountant.statutory.services;

/** Supplies the current {@link GstParameters}; one snapshot is read per unit of work. */
@FunctionalInterface
public interface GstParameterSource {

    GstParameters current();
}
