package com.hsb.createhsbbill.model;

import java.time.LocalDateTime;

/**
 * One transaction or payment line, ready to print - the Java equivalent
 * of one row in the old Delphi scratch table PAYMENT_&lt;area&gt;
 * (BillPrint.pas, InsertTxnData / OpenTxnData, lines 1958-2040).
 *
 * The old code built this by copying rows from two different source
 * tables (transac, payment) into one common shape, then reading them
 * back in date order. We do the same reshaping here, but in memory only -
 * see TransactionRepository. Nothing is written to a temporary table.
 */
public class TransactionRow {

    private String accNbr;
    private int billCycle;
    private String tranType;   // transac.txn_type, or fixed 'PAYM' for a payment
    private String tranDesc;   // SeekDesc(txn_type), or fixed 'Payment'
    private double tranAmt;
    private String status;     // transac.amt_stat, or fixed '-1' for a payment
    private LocalDateTime tranDate;

    public TransactionRow() {
    }

    public TransactionRow(String accNbr, int billCycle, String tranType, String tranDesc,
                           double tranAmt, String status, LocalDateTime tranDate) {
        this.accNbr = accNbr;
        this.billCycle = billCycle;
        this.tranType = tranType;
        this.tranDesc = tranDesc;
        this.tranAmt = tranAmt;
        this.status = status;
        this.tranDate = tranDate;
    }

    public String getAccNbr() {
        return accNbr;
    }

    public int getBillCycle() {
        return billCycle;
    }

    public String getTranType() {
        return tranType;
    }

    public String getTranDesc() {
        return tranDesc;
    }

    public double getTranAmt() {
        return tranAmt;
    }

    public String getStatus() {
        return status;
    }

    public LocalDateTime getTranDate() {
        return tranDate;
    }
}
