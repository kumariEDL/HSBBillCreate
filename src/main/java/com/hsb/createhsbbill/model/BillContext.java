package com.hsb.createhsbbill.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * All the "working state" that the old Delphi TBillPrint object kept as
 * instance fields (BillPrint.pas, lines 10-87) while it printed one whole
 * area+cycle. In Java, this is a plain object created fresh for each API
 * call, instead of fields on a shared, reused object - this keeps the
 * service classes safe to use for many requests at the same time (the
 * old Delphi object was only ever used for one print job at a time, so
 * this difference does not change the calculation itself, only how it is
 * stored while running).
 *
 * Field names below match the Delphi field they replace, so this can be
 * checked directly against BillPrint.pas.
 */
public class BillContext {

    // ----- set once, at the very start of the run -----
    private String areaCode;
    private int billCycle;
    private int cmdBillFormat;         // 0 or 1 - CmdBillFormat
    private boolean optMethIntra;      // OptMethIntra
    private String prnBillType = "0";  // Prn_BillType ('0' normal, '2' = last-cycle reprint)
    // AccNo_Prnt removed - whether the old account number is shown is now
    // decided per account from real data, not a fixed setting (see
    // hasOldAccNbr in BillCalculationService.generatePage).

    // ----- built once per run, before any account is processed -----
    private String billMonth;          // Bill_Month, e.g. "2010 Nov"
    private LocalDate trifDate;         // Trif_Date - the tariff-lookup date for this cycle
    private LocalDate maxCrdtDate;       // Max_CrdtDate - the "pay by" date
    private String messageLine1 = "";
    private String messageLine2 = "";
    private String messageLine3 = "";
    private String messageLine4 = "";
    private int maxTxnLines;           // Max_Txn_Lines - 10 or 12, depending on cmdBillFormat

    // ----- reset at the start of EACH account -----
    private String oldAccNbr = "";     // Old_Accnbr
    private String invoiceNbr = "";    // Invoice_Nbr - this account's invoice number
    private BigDecimal bfBal = BigDecimal.ZERO;      // Bf_Bal
    private BigDecimal fixedChg = BigDecimal.ZERO;   // Fixed_Chg
    private BigDecimal totGst = BigDecimal.ZERO;     // Tot_Gst
    private BigDecimal totAmt = BigDecimal.ZERO;     // Tot_Amt
    private BigDecimal totKwo = BigDecimal.ZERO;     // Tot_Kwo
    private BigDecimal totKwd = BigDecimal.ZERO;     // Tot_Kwd
    private BigDecimal totKwp = BigDecimal.ZERO;     // Tot_Kwp
    private BigDecimal fac = BigDecimal.ZERO;        // Fac
    private BigDecimal kwoRate = BigDecimal.ZERO;    // Kwo_Rate
    private BigDecimal kwdRate = BigDecimal.ZERO;    // Kwd_Rate
    private BigDecimal kwpRate = BigDecimal.ZERO;    // Kwp_Rate
    private BigDecimal tmpKwoRate = BigDecimal.ZERO; // Tmp_Kwo_Rate
    private BigDecimal fuelCharge = BigDecimal.ZERO; // Fuel_Charge
    private BigDecimal fuelGstCharge = BigDecimal.ZERO; // Fuel_GstCharge
    private BigDecimal tarff = BigDecimal.ZERO;      // unused placeholder (kept for symmetry)
    private String lastTariff = "";    // tarff - the last Cst_Trf value seen, used for the fuel % check
    private boolean gstApplicable;     // GSTApplicable

    // ----- running totals across the transaction-history list -----
    private BigDecimal txnTot = BigDecimal.ZERO;     // Txn_Tot
    private int txnCounter;            // Txn_Counter
    private String txnDate = "";       // Txn_Date - last date line printed, to group same-day rows

    // ----- page / sheet bookkeeping -----
    private int pageCounter = 1;       // Page_counter
    private int mtrSeq = 1;            // Mtr_Seq - which physical meter set (1, 2, 3...)
    private List<String> accList;              // AccList - every account, in print order
    private List<Integer> sheetsPerAcc;        // Nbrof_SheetListPerAcc - sheets needed, same order as accList

    // getters and setters

    public String getAreaCode() {
        return areaCode;
    }

    public void setAreaCode(String areaCode) {
        this.areaCode = areaCode;
    }

    public int getBillCycle() {
        return billCycle;
    }

    public void setBillCycle(int billCycle) {
        this.billCycle = billCycle;
    }

    public int getCmdBillFormat() {
        return cmdBillFormat;
    }

    public void setCmdBillFormat(int cmdBillFormat) {
        this.cmdBillFormat = cmdBillFormat;
    }

    public boolean isOptMethIntra() {
        return optMethIntra;
    }

    public void setOptMethIntra(boolean optMethIntra) {
        this.optMethIntra = optMethIntra;
    }

    public String getPrnBillType() {
        return prnBillType;
    }

    public void setPrnBillType(String prnBillType) {
        this.prnBillType = prnBillType;
    }

    public String getBillMonth() {
        return billMonth;
    }

    public void setBillMonth(String billMonth) {
        this.billMonth = billMonth;
    }

    public LocalDate getTrifDate() {
        return trifDate;
    }

    public void setTrifDate(LocalDate trifDate) {
        this.trifDate = trifDate;
    }

    public LocalDate getMaxCrdtDate() {
        return maxCrdtDate;
    }

    public void setMaxCrdtDate(LocalDate maxCrdtDate) {
        this.maxCrdtDate = maxCrdtDate;
    }

    public String getMessageLine1() {
        return messageLine1;
    }

    public void setMessageLine1(String messageLine1) {
        this.messageLine1 = messageLine1;
    }

    public String getMessageLine2() {
        return messageLine2;
    }

    public void setMessageLine2(String messageLine2) {
        this.messageLine2 = messageLine2;
    }

    public String getMessageLine3() {
        return messageLine3;
    }

    public void setMessageLine3(String messageLine3) {
        this.messageLine3 = messageLine3;
    }

    public String getMessageLine4() {
        return messageLine4;
    }

    public void setMessageLine4(String messageLine4) {
        this.messageLine4 = messageLine4;
    }

    public int getMaxTxnLines() {
        return maxTxnLines;
    }

    public void setMaxTxnLines(int maxTxnLines) {
        this.maxTxnLines = maxTxnLines;
    }

    public String getOldAccNbr() {
        return oldAccNbr;
    }

    public void setOldAccNbr(String oldAccNbr) {
        this.oldAccNbr = oldAccNbr;
    }

    public String getInvoiceNbr() {
        return invoiceNbr;
    }

    public void setInvoiceNbr(String invoiceNbr) {
        this.invoiceNbr = invoiceNbr;
    }

    public BigDecimal getBfBal() {
        return bfBal;
    }

    public void setBfBal(BigDecimal bfBal) {
        this.bfBal = bfBal;
    }

    public BigDecimal getFixedChg() {
        return fixedChg;
    }

    public void setFixedChg(BigDecimal fixedChg) {
        this.fixedChg = fixedChg;
    }

    public BigDecimal getTotGst() {
        return totGst;
    }

    public void setTotGst(BigDecimal totGst) {
        this.totGst = totGst;
    }

    public BigDecimal getTotAmt() {
        return totAmt;
    }

    public void setTotAmt(BigDecimal totAmt) {
        this.totAmt = totAmt;
    }

    public BigDecimal getTotKwo() {
        return totKwo;
    }

    public void setTotKwo(BigDecimal totKwo) {
        this.totKwo = totKwo;
    }

    public BigDecimal getTotKwd() {
        return totKwd;
    }

    public void setTotKwd(BigDecimal totKwd) {
        this.totKwd = totKwd;
    }

    public BigDecimal getTotKwp() {
        return totKwp;
    }

    public void setTotKwp(BigDecimal totKwp) {
        this.totKwp = totKwp;
    }

    public BigDecimal getFac() {
        return fac;
    }

    public void setFac(BigDecimal fac) {
        this.fac = fac;
    }

    public BigDecimal getKwoRate() {
        return kwoRate;
    }

    public void setKwoRate(BigDecimal kwoRate) {
        this.kwoRate = kwoRate;
    }

    public BigDecimal getKwdRate() {
        return kwdRate;
    }

    public void setKwdRate(BigDecimal kwdRate) {
        this.kwdRate = kwdRate;
    }

    public BigDecimal getKwpRate() {
        return kwpRate;
    }

    public void setKwpRate(BigDecimal kwpRate) {
        this.kwpRate = kwpRate;
    }

    public BigDecimal getTmpKwoRate() {
        return tmpKwoRate;
    }

    public void setTmpKwoRate(BigDecimal tmpKwoRate) {
        this.tmpKwoRate = tmpKwoRate;
    }

    public BigDecimal getFuelCharge() {
        return fuelCharge;
    }

    public void setFuelCharge(BigDecimal fuelCharge) {
        this.fuelCharge = fuelCharge;
    }

    public BigDecimal getFuelGstCharge() {
        return fuelGstCharge;
    }

    public void setFuelGstCharge(BigDecimal fuelGstCharge) {
        this.fuelGstCharge = fuelGstCharge;
    }

    public String getLastTariff() {
        return lastTariff;
    }

    public void setLastTariff(String lastTariff) {
        this.lastTariff = lastTariff;
    }

    public boolean isGstApplicable() {
        return gstApplicable;
    }

    public void setGstApplicable(boolean gstApplicable) {
        this.gstApplicable = gstApplicable;
    }

    public BigDecimal getTxnTot() {
        return txnTot;
    }

    public void setTxnTot(BigDecimal txnTot) {
        this.txnTot = txnTot;
    }

    public int getTxnCounter() {
        return txnCounter;
    }

    public void setTxnCounter(int txnCounter) {
        this.txnCounter = txnCounter;
    }

    public String getTxnDate() {
        return txnDate;
    }

    public void setTxnDate(String txnDate) {
        this.txnDate = txnDate;
    }

    public int getPageCounter() {
        return pageCounter;
    }

    public void setPageCounter(int pageCounter) {
        this.pageCounter = pageCounter;
    }

    public int getMtrSeq() {
        return mtrSeq;
    }

    public void setMtrSeq(int mtrSeq) {
        this.mtrSeq = mtrSeq;
    }

    public List<String> getAccList() {
        return accList;
    }

    public void setAccList(List<String> accList) {
        this.accList = accList;
    }

    public List<Integer> getSheetsPerAcc() {
        return sheetsPerAcc;
    }

    public void setSheetsPerAcc(List<Integer> sheetsPerAcc) {
        this.sheetsPerAcc = sheetsPerAcc;
    }
}
