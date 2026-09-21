package com.hsb.createhsbbill.model;

import java.math.BigDecimal;

/**
 * BillPrint.pas, OpenMonTot (line 1922): the monthly summary totals for
 * one account, read from table mon_tot. "fac" is calculated inside the
 * SQL itself: tot_charge - (tot_kwochg + tot_kwpchg + tot_kwdchg + tot_kvachg).
 * All values default to 0.00 if the account has no row for this cycle.
 */
public record MonthlyTotals(
        BigDecimal bfBal,
        BigDecimal fixedChg,
        BigDecimal totGst,
        BigDecimal totAmt,
        BigDecimal totKwo,
        BigDecimal totKwp,
        BigDecimal totKwd,
        BigDecimal fac) {

    public static MonthlyTotals zero() {
        BigDecimal z = new BigDecimal("0.00");
        return new MonthlyTotals(z, z, z, z, z, z, z, z);
    }
}
