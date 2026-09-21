package com.hsb.createhsbbill.repository;

import com.hsb.createhsbbill.model.MonthlyTotals;
import com.hsb.createhsbbill.model.StopChqMessages;
import com.hsb.createhsbbill.model.TaxInvoiceResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Every internal lookup function BillPrint.pas calls, that is a plain,
 * single-purpose database read (not the main readings query, and not the
 * transaction/payment history - see ReadingRepository and
 * TransactionRepository for those). Each method below is one function
 * from BillPrint.pas, named the same, with its exact original line noted.
 */
@Repository
public class ReferenceDataRepository {

    private final JdbcTemplate jdbcTemplate;

    public ReferenceDataRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** BillPrint.pas, GetOldAccNbr (line 2059). "" if not found. */
    public String getOldAccNbr(String newAccNbr) {
        List<String> result = jdbcTemplate.query(
                "select old_acc from spsd_acc where new_acc = ?",
                (rs, rowNum) -> rs.getString("old_acc"),
                newAccNbr);
        return result.isEmpty() ? "" : result.get(0);
    }

    /**
     * BillPrint.pas, Get_Tax_Inv (line 434).
     *
     * FIXED: the original had no "not found" check (a real defect flagged
     * earlier, alongside Get_BillMonth). Here, an account not found in
     * `customer` is treated the same as tax_inv <> 'Y' - not a tax
     * invoice, no tax number - rather than crashing.
     */
    public TaxInvoiceResult getTaxInv(String accNbr) {
        List<TaxInvoiceResult> result = jdbcTemplate.query(
                "select tax_inv, tax_num from customer where acc_nbr = ?",
                (rs, rowNum) -> {
                    boolean isTaxInv = "Y".equals(rs.getString("tax_inv"));
                    return new TaxInvoiceResult(isTaxInv, isTaxInv ? rs.getString("tax_num") : "");
                },
                accNbr);
        return result.isEmpty() ? new TaxInvoiceResult(false, "") : result.get(0);
    }

    /** BillPrint.pas, FindTariffDesc (line 1762). "" if not found. */
    public String findTariffDesc(String tariff) {
        List<String> result = jdbcTemplate.query(
                "select tariff_desc from tariff_desc where tariff = ?",
                (rs, rowNum) -> rs.getString("tariff_desc"),
                tariff);
        return result.isEmpty() ? "" : result.get(0);
    }

    /** BillPrint.pas, GetMtrTypeDesc (line 2100). "" if not found. */
    public String getMtrTypeDesc(String mtrType, String custCat) {
        List<String> result = jdbcTemplate.query(
                "select mtr_desc from meter_type where mtr_type = ? and cus_cat = ?",
                (rs, rowNum) -> rs.getString("mtr_desc"),
                mtrType, custCat);
        return result.isEmpty() ? "" : result.get(0);
    }

    /** BillPrint.pas, IsFuelApplicable (line 334) - government hospitals
     *  are exempt from the fuel adjustment charge. */
    public boolean isFuelApplicable(String accNbr) {
        List<String> result = jdbcTemplate.query(
                "select acc_nbr from gov_hospitals where acc_nbr = ?",
                (rs, rowNum) -> rs.getString("acc_nbr"),
                accNbr);
        return !result.isEmpty();
    }

    /** BillPrint.pas, GenMtrAvgs (line 2042). 0.00 if not found. */
    public BigDecimal genMtrAvgs(String instId, String mtrType, int mtrSeq) {
        List<BigDecimal> result = jdbcTemplate.query(
                "select avg_cnsp_3 from mtr_detail where inst_id = ? and mtr_seq = ? and mtr_type = ?",
                (rs, rowNum) -> rs.getBigDecimal("avg_cnsp_3"),
                instId, mtrSeq, mtrType);
        return result.isEmpty() ? new BigDecimal("0.00") : result.get(0);
    }

    /** BillPrint.pas, GetConcessionBlk (line 355). 0 if not found. */
    public int getConcessionBlk(String accNbr) {
        List<Integer> result = jdbcTemplate.query(
                "select first_blk_units from concession where acc_nbr = ?",
                (rs, rowNum) -> rs.getInt("first_blk_units"),
                accNbr);
        return result.isEmpty() ? 0 : result.get(0);
    }

    /**
     * BillPrint.pas, GetBFBalDate (line 2135). Today's date if not found;
     * otherwise the stored date PLUS ONE DAY - note the +1, which is the
     * one difference from getMaxCrdtDate below, reading the same table.
     */
    public LocalDate getBFBalDate(int billCycle, String areaCode) {
        // Informix's JDBC driver does not correctly support
        // getObject(column, LocalDate.class) - confirmed by a real
        // ClassCastException during testing - so getDate() + toLocalDate()
        // is used instead, which works with it.
        List<LocalDate> result = jdbcTemplate.query(
                "select to_date from pay_cyc_update where crnt_cycle = ? and area_cd = ?",
                (rs, rowNum) -> rs.getDate("to_date").toLocalDate(),
                billCycle, areaCode);
        return result.isEmpty() ? LocalDate.now() : result.get(0).plusDays(1);
    }

    /**
     * BillPrint.pas, GetMaxCrdtDate (line 1805). Today's date if not
     * found; otherwise the stored date AS-IS (no +1 - unlike getBFBalDate
     * above, even though it reads the same table).
     */
    public LocalDate getMaxCrdtDate(int billCycle, String areaCode) {
        List<LocalDate> result = jdbcTemplate.query(
                "select to_date from pay_cyc_update where crnt_cycle = ? and area_cd = ?",
                (rs, rowNum) -> rs.getDate("to_date").toLocalDate(),
                billCycle, areaCode);
        return result.isEmpty() ? LocalDate.now() : result.get(0);
    }

    /**
     * BillPrint.pas, OpenMonTot (line 1922). All zero if the account has
     * no row for this cycle. "fac" is computed inside the SQL itself.
     * bfBal is rounded to 2 decimal places, matching the original's
     * Format('%10.2f', ...) round-trip.
     */
    public MonthlyTotals openMonTot(String accNbr, int billCycle) {
        // mon_tot.bill_cycle is char(3), confirmed against the real schema -
        // not a number, so it must be compared against text, not an int.
        String billCycleText = com.hsb.createhsbbill.util.BillDateUtils.billCycleAsChar3(billCycle);
        List<MonthlyTotals> result = jdbcTemplate.query(
                "select bf_bal, fixed_chg, tot_gst, tot_amt, tot_untskwo, tot_untskwp, tot_untskwd, "
                        + "tot_charge - (tot_kwochg + tot_kwpchg + tot_kwdchg + tot_kvachg) as fac "
                        + "from mon_tot where acc_nbr = ? and bill_cycle = ?",
                (rs, rowNum) -> new MonthlyTotals(
                        rs.getBigDecimal("bf_bal").setScale(2, java.math.RoundingMode.HALF_UP),
                        rs.getBigDecimal("fixed_chg"),
                        rs.getBigDecimal("tot_gst"),
                        rs.getBigDecimal("tot_amt"),
                        rs.getBigDecimal("tot_untskwo"),
                        rs.getBigDecimal("tot_untskwp"),
                        rs.getBigDecimal("tot_untskwd"),
                        rs.getBigDecimal("fac")),
                accNbr, billCycleText);
        return result.isEmpty() ? MonthlyTotals.zero() : result.get(0);
    }

    /** BillPrint.pas, NbrofMtrSet (line 315) - number of physical meter
     *  sets installed at this installation. 0 if not found. */
    public int nbrofMtrSet(String instId) {
        List<Integer> result = jdbcTemplate.query(
                "select mtr_set from inst_info where inst_id = ?",
                (rs, rowNum) -> rs.getInt("mtr_set"),
                instId);
        return result.isEmpty() ? 0 : result.get(0);
    }

    /**
     * BillPrint.pas, NbrofTxn (line 1649). tableName must be exactly
     * "transac" or "payment" - the filter columns differ between them,
     * matching the original's if/else on the table name.
     */
    public int nbrofTxn(String accNbr, String tableName, int billCycle) {
        if ("transac".equals(tableName)) {
            // transac.proc_blcy is char(3), confirmed against the real
            // schema - text, not a number.
            String billCycleText = com.hsb.createhsbbill.util.BillDateUtils.billCycleAsChar3(billCycle);
            Integer count = jdbcTemplate.queryForObject(
                    "select count(*) from transac where acc_nbr = ? and proc_blcy = ? and txn_amt <> 0",
                    Integer.class, accNbr, billCycleText);
            return count == null ? 0 : count;
        }
        if ("payment".equals(tableName)) {
            Integer count = jdbcTemplate.queryForObject(
                    "select count(*) from payment where acc_nbr = ? and pro_blcy = ?",
                    Integer.class, accNbr, billCycle);
            return count == null ? 0 : count;
        }
        throw new IllegalArgumentException("nbrofTxn only supports 'transac' or 'payment', got: " + tableName);
    }

    /**
     * BillPrint.pas, SeekDesc (line 1779) - a 2-step fallback: try
     * jnal_typs first, then ln_type if nothing was found there.
     */
    public String seekDesc(String jnlType) {
        List<String> fromJnalTypes = jdbcTemplate.query(
                "select jnl_desc from jnal_typs where jnl_type = ?",
                (rs, rowNum) -> rs.getString("jnl_desc"),
                jnlType);
        if (!fromJnalTypes.isEmpty()) {
            return fromJnalTypes.get(0);
        }
        List<String> fromLnType = jdbcTemplate.query(
                "select lnt_desc from ln_type where ln_type = ?",
                (rs, rowNum) -> rs.getString("lnt_desc"),
                jnlType);
        return fromLnType.isEmpty() ? "" : fromLnType.get(0);
    }

    /**
     * BillPrint.pas, OpenStopChq (line 1880) - the set of account numbers
     * flagged for a stop-cheque warning message this cycle.
     */
    public Set<String> openStopChq(int billCycle) {
        List<String> flagged = jdbcTemplate.query(
                "select acc_nbr from stop_chqpay where crnt_bill_cycle = ? and stop_on_off = '1'",
                (rs, rowNum) -> rs.getString("acc_nbr"),
                billCycle);
        return new HashSet<>(flagged);
    }

    /** BillPrint.pas, GetStopChqMsg (line 1894). All blank if not found. */
    public StopChqMessages getStopChqMsg(int billCycle) {
        List<StopChqMessages> result = jdbcTemplate.query(
                "select prnt_messg1, prnt_messg2, prnt_messg3, prnt_messg4 "
                        + "from stop_chqmessage where bill_cycle = ?",
                (rs, rowNum) -> new StopChqMessages(
                        rs.getString("prnt_messg1"),
                        rs.getString("prnt_messg2"),
                        rs.getString("prnt_messg3"),
                        rs.getString("prnt_messg4")),
                billCycle);
        return result.isEmpty() ? StopChqMessages.blank() : result.get(0);
    }

    /**
     * BillPrint.pas, NbrofSheetsPerAcc (line 1687): how many sheets one
     * account needs, based on its transaction + payment history.
     * sheets = ceiling( (transactions + payments - constLines) / maxLen + 1 )
     */
    public int nbrofSheetsPerAcc(String accNbr, int maxLen, int constLines, int billCycle) {
        int txnCount = nbrofTxn(accNbr, "transac", billCycle);
        int paymtCount = nbrofTxn(accNbr, "payment", billCycle);
        double calVal = ((double) (txnCount + paymtCount) - constLines) / maxLen + 1;
        double fracVal = calVal - Math.floor(calVal);
        return fracVal == 0 ? (int) Math.floor(calVal) : (int) Math.floor(calVal) + 1;
    }

    /**
     * BillPrint.pas, WritetoBillFormat (line 1834) - an audit record of
     * the first and last account/invoice printed in this run.
     *
     * writeMode "0" = insert the "first" row; "1" = update the "last"
     * columns. Any other value in the original deletes every row in the
     * table with no filter at all - that branch is never actually
     * triggered by any real caller, so it is deliberately NOT reproduced
     * here (see "Known issues in the original code").
     */
    public void writetoBillFormat(String writeMode, String areaCode, int billCycle,
                                   String accNbr, String invNbr, String redCd, String dlyPk,
                                   String ordPrn, String accNoPrnt, String custCatPrn, String methPrn,
                                   String billFormat) {
        if ("0".equals(writeMode)) {
            jdbcTemplate.update(
                    "insert into prn_bill_format (area_cd, bill_cycle, ord_of_print, acc_no_prnt, "
                            + "meth_of_prnt, cust_cat, bill_format, first_acc_nbr, first_inv_nbr, "
                            + "first_red_code, first_dly_pack, prntd_date) "
                            + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    areaCode, billCycle, ordPrn, accNoPrnt, methPrn, custCatPrn, billFormat,
                    accNbr, invNbr, redCd, dlyPk, java.time.LocalDateTime.now());
        } else if ("1".equals(writeMode)) {
            jdbcTemplate.update(
                    "update prn_bill_format set last_acc_nbr = ?, last_inv_nbr = ?, "
                            + "last_red_code = ?, last_dly_pack = ? where area_cd = ? and bill_cycle = ?",
                    accNbr, invNbr, redCd, dlyPk, areaCode, billCycle);
        }
    }
}
