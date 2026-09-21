package com.hsb.createhsbbill.service;

import com.hsb.createhsbbill.model.*;
import com.hsb.createhsbbill.repository.*;
import com.hsb.createhsbbill.util.BillDateUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

/**
 * The real bill calculation - the Java translation of
 * TBillPrint.bill_pint_from_table (BillPrint.pas, lines 622-1641), its
 * setup step IniPrintVal (lines 554-610), the page-count setup
 * NbrofSheetsPerArea (lines 1700-1743), and the extra fields added by the
 * override TMonthlyBill.bill_pint_from_table (fsHsb86.pas, lines 529-605).
 *
 * Every block below is commented with the exact original line numbers it
 * replaces, so it can be checked directly against BillPrint.pas.
 *
 * One interpretation worth confirming with madam: mtr_seq is read here as
 * "which physical meter SET at this installation" (an account with more
 * than one meter set gets one extra bill page per extra set) - this
 * matches how NbrofMtrSet/NbrofSheetsPerArea use it, but the original
 * code never states this in so many words.
 */
@Service
public class BillCalculationService {

    private static final String REPORT_TYPE = "Priyanka"; // kept exactly as the original used it

    private final ReadingRepository readingRepository;
    private final ReferenceDataRepository referenceDataRepository;
    private final TransactionRepository transactionRepository;
    private final TariffRepository tariffRepository;

    public BillCalculationService(ReadingRepository readingRepository,
                                   ReferenceDataRepository referenceDataRepository,
                                   TransactionRepository transactionRepository,
                                   TariffRepository tariffRepository) {
        this.readingRepository = readingRepository;
        this.referenceDataRepository = referenceDataRepository;
        this.transactionRepository = transactionRepository;
        this.tariffRepository = tariffRepository;
    }

    /**
     * The whole run, for one area + bill cycle - every field, for every
     * account, for every page. This is the equivalent of: BtnOkClick's
     * setup calls, then IniPrintVal, then the loop that calls
     * bill_pint_from_table once per sheet (fsHsb86.pas, PrintMnthlBills,
     * lines 607-696).
     */
    public List<BillPrintFieldRow> generateBill(String areaCode, int billCycle) {
        List<ReadingRow> allReadings = readingRepository.loadReadings(areaCode, billCycle);
        if (allReadings.isEmpty()) {
            return List.of();
        }

        // Group readings by account, keeping the query's own order
        // (already sorted by red_code, dly_pack, wlk_ord).
        Map<String, List<ReadingRow>> readingsByAccount = new LinkedHashMap<>();
        for (ReadingRow r : allReadings) {
            readingsByAccount.computeIfAbsent(r.getAccNbr(), k -> new ArrayList<>()).add(r);
        }

        BillContext ctx = new BillContext();
        ctx.setAreaCode(areaCode);
        ctx.setBillCycle(billCycle);
        ctx.setCmdBillFormat(0);       // Max_Len=10 layout - see BtnOkClick, line 752-756
        ctx.setOptMethIntra(false);    // this API always runs the normal, non-interactive mode
        ctx.setPrnBillType("0");
        // Note: whether the old account number is shown is no longer a
        // fixed setting - it's decided per account, from real data
        // (see hasOldAccNbr in generatePage).

        // ----- NbrofSheetsPerArea (lines 1700-1743): build AccList + sheet counts -----
        int maxLen = ctx.getCmdBillFormat() == 0 ? 10 : 12;
        int constLines = 5;
        List<String> accList = new ArrayList<>(readingsByAccount.keySet());
        List<Integer> sheetsPerAcc = new ArrayList<>();
        for (String accNbr : accList) {
            ReadingRow first = readingsByAccount.get(accNbr).get(0);
            int byTxnHistory = referenceDataRepository.nbrofSheetsPerAcc(accNbr, maxLen, constLines, billCycle);
            int byMeterSets = referenceDataRepository.nbrofMtrSet(first.getInstId());
            sheetsPerAcc.add(Math.max(byTxnHistory, byMeterSets));
        }
        ctx.setAccList(accList);
        ctx.setSheetsPerAcc(sheetsPerAcc);

        // ----- IniPrintVal (lines 554-610): once-per-run setup -----
        ctx.setMaxTxnLines(ctx.getCmdBillFormat() == 0 ? 10 : 12);
        String billMonth = tariffRepository.getBillMonth(billCycle);
        ctx.setBillMonth(billMonth);
        ctx.setTrifDate(BillDateUtils.toTrifDate(billMonth));
        StopChqMessages messages = referenceDataRepository.getStopChqMsg(billCycle);
        ctx.setMessageLine1(messages.line1());
        ctx.setMessageLine2(messages.line2());
        ctx.setMessageLine3(messages.line3());
        ctx.setMessageLine4(messages.line4());
        Set<String> stopChqAccounts = referenceDataRepository.openStopChq(billCycle);

        // ----- the main loop: one account at a time, one page at a time -----
        List<BillPrintFieldRow> allRows = new ArrayList<>();
        for (int accIndex = 0; accIndex < accList.size(); accIndex++) {
            String accNbr = accList.get(accIndex);
            List<ReadingRow> accountReadings = readingsByAccount.get(accNbr);
            int sheetsForThisAccount = sheetsPerAcc.get(accIndex);

            // one meter-set's readings per page (mtr_seq = 1, 2, 3...)
            Map<Integer, List<ReadingRow>> byMtrSeq = new TreeMap<>();
            for (ReadingRow r : accountReadings) {
                byMtrSeq.computeIfAbsent(r.getMtrSeq(), k -> new ArrayList<>()).add(r);
            }

            // per-account state, reset before the first page (IniPrintVal, lines 576-593)
            MonthlyTotals monTot = referenceDataRepository.openMonTot(accNbr, billCycle);
            applyMonthlyTotals(ctx, monTot);
            ctx.setOldAccNbr(referenceDataRepository.getOldAccNbr(accNbr));
            ctx.setMaxCrdtDate(referenceDataRepository.getMaxCrdtDate(billCycle, areaCode));
            List<TransactionRow> txns = transactionRepository.loadTransactionsAndPayments(
                    accNbr, billCycle, referenceDataRepository::seekDesc);
            ctx.setInvoiceNbr(generateInvoiceNbr(areaCode, billCycle, accIndex));
            ctx.setTxnCounter(0);
            ctx.setTxnDate("");

            int pageCounter = 1;
            for (Map.Entry<Integer, List<ReadingRow>> pageEntry : byMtrSeq.entrySet()) {
                ctx.setPageCounter(pageCounter);
                ctx.setMtrSeq(pageEntry.getKey());
                boolean isLastPageForAccount = pageCounter == sheetsForThisAccount;

                allRows.addAll(generatePage(ctx, accNbr, pageEntry.getValue(), txns,
                        isLastPageForAccount, stopChqAccounts.contains(accNbr)));

                pageCounter++;
            }

            // WritetoBillFormat (lines 592-593, 1636-1637): first/last audit record
            String first = accList.get(0);
            referenceDataRepository.writetoBillFormat(
                    accIndex == 0 ? "0" : "1", areaCode, billCycle, accNbr, ctx.getInvoiceNbr(),
                    accountReadings.get(0).getRedCode(), accountReadings.get(0).getDlyPack(),
                    "R", "AUTO", "", "A", String.valueOf(ctx.getCmdBillFormat()));
        }

        return allRows;
    }

    private void applyMonthlyTotals(BillContext ctx, MonthlyTotals m) {
        ctx.setBfBal(m.bfBal());
        ctx.setFixedChg(m.fixedChg());
        ctx.setTotGst(m.totGst());
        ctx.setTotAmt(m.totAmt());
        ctx.setTotKwo(m.totKwo());
        ctx.setTotKwp(m.totKwp());
        ctx.setTotKwd(m.totKwd());
        ctx.setFac(m.fac());
    }

    /**
     * BillPrint.pas, GenInvoiceNbr (line 2117): areaCode + billCycle + a
     * 5-digit sequence number, one higher each time. Reproduced here as a
     * simple running counter per run, since this API generates a whole
     * area+cycle in one call rather than incrementing account by account
     * across separate requests, as the original interactive screen did.
     */
    private String generateInvoiceNbr(String areaCode, int billCycle, int sequenceFromZero) {
        int seq = sequenceFromZero + 1;
        return areaCode + billCycle + String.format("%05d", seq);
    }

    /**
     * BillPrint.pas, bill_pint_from_table (lines 622-1641) plus the extra
     * fields from fsHsb86.pas's override (lines 529-605). Builds every
     * printable field for ONE page of ONE account.
     */
    private List<BillPrintFieldRow> generatePage(BillContext ctx, String accNbr,
                                                  List<ReadingRow> pageReadings,
                                                  List<TransactionRow> txns,
                                                  boolean isLastPageForAccount,
                                                  boolean isStopChqFlagged) {
        List<BillPrintFieldRow> rows = new ArrayList<>();
        ReadingRow base = pageReadings.get(0); // customer-level fields are the same on every row here
        int billCycle = ctx.getBillCycle();
        int pageNo = ctx.getPageCounter();

        // ----- lines 695-727: header, tax invoice, area line -----
        TaxInvoiceResult taxInv = referenceDataRepository.getTaxInv(accNbr);
        if (taxInv.taxInvoice()) {
            add(rows, accNbr, billCycle, pageNo, "1.1", 280, 810, "Arial Black", "12", "Regular",
                    "C", "Tax Invoice", 0, null, null);
            add(rows, accNbr, billCycle, pageNo, "1.1", 370, 825, "Courier New", "10", "Regular",
                    "C", "VAT Reg. No. :" + taxInv.taxNumber(), 0, null, null);
        }
        String areaName = tariffRepository.setAreaName(ctx.getAreaCode());
        add(rows, accNbr, billCycle, pageNo, "1.2", 280, 790, "Courier New", "10", "Regular", "C",
                base.getRedCode() + "-" + base.getDlyPack() + "-" + base.getWlkOrd() + "        " + areaName,
                0, null, null);

        // ----- lines 743-760: name, address, invoice number -----
        add(rows, accNbr, billCycle, pageNo, "1.0", 45, 765, "Courier New", "10", "Regular", "C",
                base.getName(), 0, null, null);
        add(rows, accNbr, billCycle, pageNo, "1.0", 45, 755, "Courier New", "10", "Regular", "C",
                base.getAddressL1(), 0, null, null);
        add(rows, accNbr, billCycle, pageNo, "1.0", 45, 745, "Courier New", "10", "Regular", "C",
                base.getAddressL2() + " " + base.getCity(), 0, null, null);
        add(rows, accNbr, billCycle, pageNo, "1.3", 460, 740, "Courier New", "10", "Regular", "C",
                ctx.getInvoiceNbr(), 0, null, null);

        // ----- lines 762-769: bill month -----
        add(rows, accNbr, billCycle, pageNo, "2.0", 100, 710, "Courier New", "10", "Regular", "C",
                ctx.getBillMonth(), 0, null, null);

        // ----- lines 771-793: account number, old account number (only if one exists), security deposit -----
        // Rule: always show the current account number. Also show the old
        // one, but only for accounts that actually have one - decided from
        // the real data (getOldAccNbr), not a fixed setting.
        add(rows, accNbr, billCycle, pageNo, "2.1", 268, 715, "Arial Black", "9", "Regular", "C",
                accNbr, 0, null, null);
        add(rows, accNbr, billCycle, pageNo, "2.2", 465, 700, "Courier New", "10", "Regular", "F", "",
                0, base.getTotSecDep(), null);
        boolean hasOldAccNbr = ctx.getOldAccNbr() != null && !ctx.getOldAccNbr().isEmpty();
        if (hasOldAccNbr) {
            add(rows, accNbr, billCycle, pageNo, "2.1", 268, 705, "Courier New", "8", "Regular", "C",
                    "Old Ac/No - " + ctx.getOldAccNbr(), 0, null, null);
        }

        // ----- lines 805-819: credit date, tariff description, contracted demand -----
        add(rows, accNbr, billCycle, pageNo, "2.3", 125, 680, "Courier New", "8", "Regular", "D", "",
                0, null, ctx.getMaxCrdtDate());
        String tariffDesc = referenceDataRepository.findTariffDesc(base.getCstTrf());
        add(rows, accNbr, billCycle, pageNo, "2.3", 250, 680, "Courier New", "8", "Regular", "C",
                tariffDesc, 0, null, null);
        ctx.setLastTariff(base.getCstTrf());
        add(rows, accNbr, billCycle, pageNo, "2.3", 465, 680, "Courier New", "8", "Regular", "F", "",
                0, base.getCntrDmnd() == null ? null : BigDecimal.valueOf(base.getCntrDmnd()), null);

        // ----- lines 821-832: reading date, previous date -----
        add(rows, accNbr, billCycle, pageNo, "3.1", 130, 635, "Courier New", "8", "Regular", "D", "",
                0, null, base.getRdngDate());
        add(rows, accNbr, billCycle, pageNo, "3.1", 200, 635, "Courier New", "8", "Regular", "D", "",
                0, null, base.getPrvDate());

        // ----- lines 834-1063: KWP / KWD / KWO / KVA meter sections -----
        BigDecimal tmpKwoRateForGst = null;
        for (String mtrType : List.of("KWP", "KWD", "KWO", "KVA")) {
            Optional<ReadingRow> maybeMeter = pageReadings.stream()
                    .filter(r -> mtrType.equals(r.getMtrType())).findFirst();
            if (maybeMeter.isEmpty()) {
                continue;
            }
            ReadingRow meter = maybeMeter.get();
            String paragraph = switch (mtrType) {
                case "KWP", "KWD" -> "3.3";
                case "KWO" -> "3.4";
                default -> "3.5"; // KVA
            };
            int baseRow = switch (mtrType) {
                case "KWP" -> 613;
                case "KWD" -> 596;
                case "KWO" -> 579;
                default -> 562; // KVA
            };

            if (ctx.getCmdBillFormat() == 0) {
                String desc = referenceDataRepository.getMtrTypeDesc(mtrType, meter.getCusCat());
                add(rows, accNbr, billCycle, pageNo, paragraph, 45, baseRow, "Courier New", "8", "Regular",
                        "C", desc, 0, null, null);
            }
            add(rows, accNbr, billCycle, pageNo, paragraph, 45, baseRow - 7, "Courier New", "8", "Regular",
                    "C", meter.getMtrNbr(), 0, null, null);

            if (!"KVA".equals(mtrType)) {
                add(rows, accNbr, billCycle, pageNo, paragraph, 260, baseRow - 17, "Courier New", "10",
                        "Regular", "N", "", meter.getRdn(), null, null);
                add(rows, accNbr, billCycle, pageNo, paragraph, 270, baseRow - 17, "Courier New", "10",
                        "Regular", "N", "", meter.getPrvRdn(), null, null);
            }
            add(rows, accNbr, billCycle, pageNo, paragraph, 275, baseRow - 17, "Courier New", "10",
                    "Regular", "N", "", meter.getUnits(), null, null);
            add(rows, accNbr, billCycle, pageNo, paragraph, 345, baseRow - 17, "Courier New", "10",
                    "Regular", "F", "", 0,
                    meter.getMFactor() == null ? null : BigDecimal.valueOf(meter.getMFactor()), null);

            if ("B".equals(meter.getCusCat())) {
                BigDecimal rate = tariffRepository.getCurrTariffRate(
                        ctx.getTrifDate(), meter.getCusCat(), meter.getCstTrf(), meter.getMtrType());
                add(rows, accNbr, billCycle, pageNo, paragraph, 425, baseRow - 17, "Courier New", "10",
                        "Regular", "F", "", 0, rate, null);
                add(rows, accNbr, billCycle, pageNo, paragraph, 465, baseRow - 17, "Courier New", "10",
                        "Regular", "F", "", 0,
                        meter.getComputedChg() == null ? null : BigDecimal.valueOf(meter.getComputedChg()), null);
                switch (mtrType) {
                    case "KWP" -> ctx.setKwpRate(rate);
                    case "KWD" -> ctx.setKwdRate(rate);
                    case "KWO" -> {
                        ctx.setKwoRate(rate);
                        ctx.setTmpKwoRate(rate);
                    }
                    default -> { /* KVA has no dedicated rate field in the original */ }
                }
            }
        }

        // ----- lines 1144-1150: KVAH meter number only -----
        pageReadings.stream().filter(r -> "KVAH".equals(r.getMtrType())).findFirst()
                .ifPresent(meter -> add(rows, accNbr, billCycle, pageNo, "3.8", 45, 520, "Courier New",
                        "8", "Regular", "C", meter.getMtrNbr(), 0, null, null));

        // ----- lines 1152-1174: 3-month average consumption per meter type -----
        for (String mtrType : List.of("KWP", "KWD", "KWO", "KVA")) {
            BigDecimal avg3 = referenceDataRepository.genMtrAvgs(base.getInstId(), mtrType, ctx.getMtrSeq());
            add(rows, accNbr, billCycle, pageNo, "3.91", 200, 530 - (5 * indexOf(mtrType)), "Courier New",
                    "8", "Regular", "C", mtrType.toLowerCase() + " avg" + String.format("%8.2f", avg3), 0,
                    null, null);
        }

        // ----- lines 1066, 1080-1093: fixed charge + fuel adjustment charge (last page only) -----
        BigDecimal fixedChg = tariffRepository.getFixedChg(ctx.getTrifDate(), base.getCusCat(), base.getCstTrf());
        ctx.setFixedChg(fixedChg);
        if (isLastPageForAccount) {
            if (ctx.getCmdBillFormat() == 0) {
                boolean noFixedCharge = List.of("RL0", "RL1", "DM1").contains(base.getCstTrf());
                if (noFixedCharge) {
                    add(rows, accNbr, billCycle, pageNo, "3.6", 1850, 1187, "Courier New", "10", "Regular",
                            "F", "", 0, BigDecimal.ZERO, null);
                } else {
                    add(rows, accNbr, billCycle, pageNo, "3.6", 532, 535, "Courier New", "10", "Regular",
                            "F", "", 0, fixedChg, null);
                }
                if ("B".equals(base.getCusCat())) {
                    boolean higherFuelGroup = List.of("GP0", "GP1", "GP2", "GP3", "GV1", "GV2", "GV3")
                            .contains(ctx.getLastTariff());
                    BigDecimal totCombined = ctx.getTotKwo().multiply(ctx.getKwoRate())
                            .add(ctx.getTotKwd().multiply(ctx.getKwdRate()))
                            .add(ctx.getTotKwp().multiply(ctx.getKwpRate()));
                    BigDecimal fuelPercent = higherFuelGroup ? new BigDecimal("0.25") : new BigDecimal("0.15");
                    BigDecimal fuelCharge = totCombined.multiply(fuelPercent).setScale(2, RoundingMode.HALF_UP);
                    if (referenceDataRepository.isFuelApplicable(accNbr)) {
                        fuelCharge = BigDecimal.ZERO;
                    }
                    ctx.setFuelCharge(fuelCharge);
                    add(rows, accNbr, billCycle, pageNo, "3.6", 532, 515, "Courier New", "10", "Regular",
                            "F", "", 0, fuelCharge, null);
                }
            } else {
                add(rows, accNbr, billCycle, pageNo, "3.6", 532, 535, "Courier New", "10", "Regular", "F",
                        "", 0, fixedChg, null);
            }
        } else {
            String msg = "Do not Write";
            if (ctx.getCmdBillFormat() == 0) {
                add(rows, accNbr, billCycle, pageNo, "3.7", 465, 540, "Courier New", "10", "Regular", "C", msg, 0, null, null);
                add(rows, accNbr, billCycle, pageNo, "3.7", 465, 520, "Courier New", "10", "Regular", "C", msg, 0, null, null);
                add(rows, accNbr, billCycle, pageNo, "3.7", 465, 500, "Courier New", "10", "Regular", "C", msg, 0, null, null);
                add(rows, accNbr, billCycle, pageNo, "3.7", 465, 480, "Courier New", "10", "Regular", "C", msg, 0, null, null);
            } else {
                add(rows, accNbr, billCycle, pageNo, "3.7", 1850, 1166, "Courier New", "10", "Regular", "C", msg, 0, null, null);
                add(rows, accNbr, billCycle, pageNo, "3.7", 1850, 1250, "Courier New", "10", "Regular", "C", msg, 0, null, null);
            }
        }

        // ----- lines 1177-1204: total this cycle, printed date, concession block -----
        add(rows, accNbr, billCycle, pageNo, "3.91", 532, 490, "Courier New", "10", "Regular", "F", "",
                0, ctx.getTotAmt(), null);
        add(rows, accNbr, billCycle, pageNo, "3.9", 42, 502, "Courier New", "8", "Regular", "C",
                "This Invoice Printed ", 0, null, null);
        add(rows, accNbr, billCycle, pageNo, "3.9", 100, 490, "Courier New", "8", "Regular", "C",
                "on  " + LocalDate.now(), 0, null, null);
        if ("O".equals(base.getCusCat())) {
            int concessionBlk = referenceDataRepository.getConcessionBlk(accNbr);
            add(rows, accNbr, billCycle, pageNo, "3.92", 120, 1418, "Courier New", "8", "Regular", "C",
                    "Concession Block :" + concessionBlk, 0, null, null);
        }

        // ----- lines 1207-1217: bill-format-1 filler rows -----
        if (ctx.getCmdBillFormat() != 0) {
            add(rows, accNbr, billCycle, pageNo, "3.93", 1125, 1313, "Courier New", "8", "Regular", "C",
                    "XXXXXXXXXXXXXXXXXXXXXXXXXXXX", 0, null, null);
            add(rows, accNbr, billCycle, pageNo, "3.93", 1125, 1355, "Courier New", "8", "Regular", "C",
                    "XXXXXXXXXXXXX", 0, null, null);
        }

        // ----- lines 1219-1247: GST applicability + max credit date (again) -----
        boolean gstApplicable = "Y".equals(base.getGstApl());
        ctx.setGstApplicable(gstApplicable);
        add(rows, accNbr, billCycle, pageNo, "4.0", 356, 460, "Courier New", "8", "Regular", "D", "",
                0, null, ctx.getMaxCrdtDate());

        // ----- lines 1250-1263: fuel charge + fuel GST (Prn_BillType = '2' only - never in this API) -----
        // Prn_BillType is always "0" for this API (see generateBill), so this block from the
        // original never applies here and is intentionally not reproduced.

        // ----- lines 1267-1327: balance brought forward + running transaction total -----
        BigDecimal txnTot = ctx.getBfBal();
        LocalDate bfBalDate = referenceDataRepository.getBFBalDate(billCycle, ctx.getAreaCode());
        if (ctx.getMtrSeq() == 1) {
            add(rows, accNbr, billCycle, pageNo, "4.0", 50, 415, "Courier New", "8", "Regular", "D", "",
                    0, null, bfBalDate);
            add(rows, accNbr, billCycle, pageNo, "4.0", 115, 415, "Courier New", "8", "Regular", "C",
                    "Balance B/F", 0, null, null);
            add(rows, accNbr, billCycle, pageNo, "4.0", 532, 415, "Courier New", "8", "Regular", "F", "",
                    0, txnTot.abs(), null);
            if (ctx.getBfBal().signum() < 0) {
                add(rows, accNbr, billCycle, pageNo, "4.0", 532, 415, "Courier New", "8", "Regular", "C",
                        "Cr", 0, null, null);
            }
            ctx.setTxnCounter(ctx.getTxnCounter() + 1);
        }
        if (pageNo > 1 && isLastPageForAccount) {
            add(rows, accNbr, billCycle, pageNo, "4.0", 115, 415, "Courier New", "8", "Regular", "C",
                    "B/F from Previous Page", 0, null, null);
            add(rows, accNbr, billCycle, pageNo, "4.0", 532, 415, "Courier New", "8", "Regular",
                    txnTot.signum() < 0 ? "F" : "C", "", 0, txnTot.abs(), null);
            if (txnTot.signum() < 0) {
                add(rows, accNbr, billCycle, pageNo, "4.0", 532, 415, "Courier New", "8", "Regular", "C",
                        "Cr", 0, null, null);
            }
            ctx.setTxnCounter(ctx.getTxnCounter() + 1);
        }
        if (pageNo == 1) {
            if (ctx.getTxnCounter() < ctx.getMaxTxnLines()) {
                txnTot = ctx.getTotAmt().subtract(ctx.getTotGst());
            }
            add(rows, accNbr, billCycle, pageNo, "4.0", 115, 400, "Courier New", "8", "Regular", "C",
                    "Charge for last month", 0, null, null);
            add(rows, accNbr, billCycle, pageNo, "4.0", 328, 400, "Courier New", "8", "Regular", "F", "",
                    0, txnTot, null);
            txnTot = txnTot.add(ctx.getBfBal());
            add(rows, accNbr, billCycle, pageNo, "4.0", 532, 400, "Courier New", "8", "Regular", "F", "",
                    0, txnTot.abs(), null);
            if (txnTot.signum() < 0) {
                add(rows, accNbr, billCycle, pageNo, "4.0", 532, 400, "Courier New", "8", "Regular", "C",
                        "Cr", 0, null, null);
            }
            ctx.setTxnCounter(ctx.getTxnCounter() + 1);

            TaxInvoiceResult taxAgain = referenceDataRepository.getTaxInv(accNbr);
            add(rows, accNbr, billCycle, pageNo, "4.0", 115, 385, "Courier New", "8", "Regular", "C",
                    taxAgain.taxInvoice() ? "VAT for last month" : "Tax Charges for last month", 0, null, null);
            add(rows, accNbr, billCycle, pageNo, "4.0", 328, 385, "Courier New", "8", "Regular", "F", "",
                    0, ctx.getTotGst(), null);
            txnTot = txnTot.add(ctx.getTotGst());
            add(rows, accNbr, billCycle, pageNo, "4.0", 532, 385, "Courier New", "8", "Regular", "F", "",
                    0, txnTot.abs(), null);
            if (txnTot.signum() < 0) {
                add(rows, accNbr, billCycle, pageNo, "4.0", 532, 385, "Courier New", "8", "Regular", "C",
                        "Cr", 0, null, null);
            }
            ctx.setTxnCounter(ctx.getTxnCounter() + 1);
        }

        // ----- lines 1424-1476: transaction / payment history rows -----
        int pos = ctx.getTxnCounter() < ctx.getMaxTxnLines() ? 370 : 415;
        if (ctx.getTxnCounter() >= ctx.getMaxTxnLines()) {
            ctx.setTxnCounter(1);
        }
        int shown = 0;
        for (TransactionRow t : txns) {
            String dateKey = t.getTranDate() == null ? "" : t.getTranDate().toLocalDate().toString();
            if (ctx.getTxnCounter() == 1 || !dateKey.equals(ctx.getTxnDate())) {
                ctx.setTxnDate(dateKey);
                add(rows, accNbr, billCycle, pageNo, "4.0", 50, pos, "Courier New", "8", "Regular", "D",
                        "", 0, null, t.getTranDate() == null ? null : t.getTranDate().toLocalDate());
            }
            add(rows, accNbr, billCycle, pageNo, "4.0", 115, pos, "Courier New", "8", "Regular", "C",
                    t.getTranDesc(), 0, null, null);
            BigDecimal amt = BigDecimal.valueOf(t.getTranAmt());
            if ("1".equals(t.getStatus())) {
                add(rows, accNbr, billCycle, pageNo, "4.0", 328, pos, "Courier New", "8", "Regular", "F",
                        "", 0, amt, null);
            } else if ("-1".equals(t.getStatus())) {
                add(rows, accNbr, billCycle, pageNo, "4.0", 423, pos, "Courier New", "8", "Regular", "F",
                        "", 0, amt, null);
            }
            int sign = "-1".equals(t.getStatus()) ? -1 : 1;
            txnTot = txnTot.add(amt.multiply(BigDecimal.valueOf(sign)));
            add(rows, accNbr, billCycle, pageNo, "4.0", 532, pos, "Courier New", "8", "Regular", "F", "",
                    0, txnTot.abs(), null);
            if (txnTot.signum() < 0) {
                add(rows, accNbr, billCycle, pageNo, "4.0", 532, pos, "Courier New", "8", "Regular", "C",
                        "Cr", 0, null, null);
            }
            ctx.setTxnCounter(ctx.getTxnCounter() + 1);
            pos -= 15;
            shown++;
            if (ctx.getTxnCounter() == ctx.getMaxTxnLines() - 2) {
                break;
            }
        }

        // ----- lines 1479-1497: running total shown again -----
        add(rows, accNbr, billCycle, pageNo, "4.0", 532, 285, "Courier New", "8", "Regular", "F", "",
                0, txnTot.abs(), null);
        if (txnTot.signum() < 0) {
            add(rows, accNbr, billCycle, pageNo, "4.0", 532, 285, "Courier New", "8", "Regular", "C",
                    "Cr", 0, null, null);
        }

        // ----- lines 1503-1544: fuel adjustment lines (bill format 1) / continue-next-page notice -----
        if (isLastPageForAccount) {
            if (ctx.getCmdBillFormat() != 0) {
                add(rows, accNbr, billCycle, pageNo, "4.0", 425, pos + 42, "Courier New", "8", "Regular",
                        "C", "Fuel Adjustment Charge", 0, null, null);
                add(rows, accNbr, billCycle, pageNo, "4.0", 1125, pos + 42, "Courier New", "8", "Regular",
                        "C", "............", 0, null, null);
                add(rows, accNbr, billCycle, pageNo, "4.0", 425, pos + 84, "Courier New", "8", "Regular",
                        "C", "GST for Fuel Adj. Charge", 0, null, null);
                add(rows, accNbr, billCycle, pageNo, "4.0", 1125, pos + 84, "Courier New", "8", "Regular",
                        "C", "............", 0, null, null);
                add(rows, accNbr, billCycle, pageNo, "4.0", 425, pos + 84, "Courier New", "8", "Regular",
                        "C", "Charge for this month", 0, null, null);
                add(rows, accNbr, billCycle, pageNo, "4.0", 1125, pos + 84, "Courier New", "8", "Regular",
                        "C", "............", 0, null, null);
            }
        } else if (ctx.getTxnCounter() < ctx.getMaxTxnLines()) {
            add(rows, accNbr, billCycle, pageNo, "4.0", 380, pos - 10, "Courier New", "8", "Regular", "C",
                    "Continue to Next Page . . .", 0, null, null);
        }

        // ----- lines 1560-1580: stop-cheque warning messages -----
        if (isStopChqFlagged) {
            add(rows, accNbr, billCycle, pageNo, "5.0", 345, 150, "Comic Sans MS", "12", "Regular", "C",
                    ctx.getMessageLine1(), 0, null, null);
            add(rows, accNbr, billCycle, pageNo, "5.0", 345, 135, "Comic Sans MS", "12", "Regular", "C",
                    ctx.getMessageLine2(), 0, null, null);
            add(rows, accNbr, billCycle, pageNo, "5.0", 345, 120, "Comic Sans MS", "12", "Regular", "C",
                    ctx.getMessageLine3(), 0, null, null);
            add(rows, accNbr, billCycle, pageNo, "5.0", 345, 105, "Comic Sans MS", "12", "Regular", "C",
                    ctx.getMessageLine4(), 0, null, null);
        }

        // ----- fsHsb86.pas override (lines 529-605): extra name/address/invoice/old-account block -----
        add(rows, accNbr, billCycle, pageNo, "7.00", 175, 205, "Courier New", "10", "Regular", "C",
                base.getName(), 0, null, null);
        add(rows, accNbr, billCycle, pageNo, "7.00", 175, 195, "Courier New", "10", "Regular", "C",
                base.getAddressL1(), 0, null, null);
        add(rows, accNbr, billCycle, pageNo, "7.00", 450, 195, "Courier New", "10", "Regular", "C",
                ctx.getInvoiceNbr(), 0, null, null);
        add(rows, accNbr, billCycle, pageNo, "7.00", 175, 185, "Courier New", "10", "Regular", "C",
                base.getAddressL2() + " " + base.getCity(), 0, null, null);
        add(rows, accNbr, billCycle, pageNo, "7.00", 115, 180, "Courier New", "10", "Regular", "C",
                ctx.getBillMonth(), 0, null, null);
        // Same rule as above: always the current account number, plus the
        // old one only when this account actually has one.
        add(rows, accNbr, billCycle, pageNo, "7.00", 115, 150, "Arial Black", "9", "Regular", "C",
                accNbr, 0, null, null);
        if (hasOldAccNbr) {
            add(rows, accNbr, billCycle, pageNo, "7.00", 115, 140, "Arial Black", "8", "Regular", "C",
                    "Old Ac/No - " + ctx.getOldAccNbr(), 0, null, null);
        }

        return rows;
    }

    private int indexOf(String mtrType) {
        return List.of("KWP", "KWD", "KWO", "KVA").indexOf(mtrType);
    }

    /** The Java equivalent of one call to Insert_data_bill_prt (BillPrint.pas, line 2165). */
    private void add(List<BillPrintFieldRow> rows, String accNbr, int billCycle, int pageNo,
                      String paragraph, double rowOrder, double colOrder,
                      String fontType, String fontSize, String fontStyle,
                      String dataType, String charVal, int intVal, BigDecimal flotVal, LocalDate dateVal) {
        BillPrintFieldRow row = new BillPrintFieldRow();
        row.setAccNbr(accNbr);
        row.setReportType(REPORT_TYPE);
        row.setBillCycle(billCycle);
        row.setAttriName1("");
        row.setPageNo(pageNo);
        row.setParagraph(new BigDecimal(paragraph));
        row.setRowOrder(BigDecimal.valueOf(rowOrder));
        row.setColOrder(BigDecimal.valueOf(colOrder));
        row.setFontType(fontType);
        row.setFontSize(fontSize);
        row.setFontStyle(fontStyle);
        row.setDataType(dataType);
        row.setCharVal(charVal == null ? "" : charVal);
        row.setIntVal(intVal);
        row.setFlotVal(flotVal == null ? new BigDecimal("0.00") : flotVal.setScale(2, RoundingMode.HALF_UP));
        row.setDateVal(dateVal);
        rows.add(row);
    }
}
