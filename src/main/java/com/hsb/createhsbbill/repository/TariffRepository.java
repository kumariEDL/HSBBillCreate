package com.hsb.createhsbbill.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The 4 external, database-backed functions from FsHsbG0.pas that
 * BillPrint.pas depends on: Set_AreaName, Get_BillMonth,
 * GetCurrTraiffRate, GetFixedChg. See "The External Seven" reference for
 * the full request/response/logic breakdown this class implements.
 */
@Repository
public class TariffRepository {

    /** A table name read back from the database is trusted data, not user
     *  input - but it is still checked before being used in SQL text, as
     *  a second safety net (same principle as BillPrintFieldRepository). */
    private static final Pattern SAFE_TABLE_NAME = Pattern.compile("^[A-Za-z0-9_]{1,30}$");

    private final JdbcTemplate jdbcTemplate;

    public TariffRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * FsHsbG0.pas, Set_AreaName (line 226):
     *   select area_name from areas where area_code = :arcode
     * Returns "" if not found - same as the original.
     */
    public String setAreaName(String areaCode) {
        List<String> result = jdbcTemplate.query(
                "select area_name from areas where area_code = ?",
                (rs, rowNum) -> rs.getString("area_name"),
                areaCode);
        return result.isEmpty() ? "" : result.get(0);
    }

    /**
     * FsHsbG0.pas, Get_BillMonth (line 212):
     *   select bill_mnth from yr_mnth where bill_cycle = :blcyl
     *
     * FIXED: the original had no "not found" check at all (a real defect
     * flagged earlier). This version throws a clear error instead of
     * failing in a confusing way further down the line.
     */
    public String getBillMonth(int billCycle) {
        List<String> result = jdbcTemplate.query(
                "select bill_mnth from yr_mnth where bill_cycle = ?",
                (rs, rowNum) -> rs.getString("bill_mnth"),
                billCycle);
        if (result.isEmpty()) {
            throw new IllegalStateException("No bill month found in yr_mnth for bill cycle " + billCycle);
        }
        return result.get(0);
    }

    /**
     * FsHsbG0.pas, GetCurrTraiffRate (line 524) - a 2-step, chained
     * lookup. Step 1 finds which table holds today's rates; step 2 reads
     * the actual rate from that table. Returns 0.00 if either step finds
     * nothing, exactly like the original.
     */
    public BigDecimal getCurrTariffRate(LocalDate trfDate, String custCat, String tariff, String mtrType) {
        List<String> step1 = jdbcTemplate.query(
                "select table_name from etariff_table "
                        + "where from_date <= ? and to_date >= ? and cus_cat = ? and tariff = ?",
                (rs, rowNum) -> rs.getString("table_name"),
                trfDate, trfDate, custCat, tariff);

        if (step1.isEmpty()) {
            return new BigDecimal("0.00");
        }
        String tableName = requireSafeTableName(step1.get(0));

        List<BigDecimal> step2 = jdbcTemplate.query(
                "select rate from " + tableName + " where tariff = ? and mtr_type = ?",
                (rs, rowNum) -> rs.getBigDecimal("rate"),
                tariff, mtrType);

        return step2.isEmpty() ? new BigDecimal("0.00") : step2.get(0);
    }

    /**
     * FsHsbG0.pas, GetFixedChg (line 412) - the same 2-step shape as
     * above, but with two differences worth remembering:
     *  - step 1 filters on the 8th CHARACTER of table_name (an
     *    Informix-only "column[n]" substring check), not a plain column
     *  - "not found" at step 1 returns -1.00 (not 0.00) - a different
     *    "not configured" signal that must be kept exactly as-is
     */
    public BigDecimal getFixedChg(LocalDate traiffDate, String custCat, String tariff) {
        List<String> step1 = jdbcTemplate.query(
                "select fixed_table from etariff_table "
                        + "where from_date <= ? and to_date >= ? and table_name[8] = ?",
                (rs, rowNum) -> rs.getString("fixed_table"),
                traiffDate, traiffDate, custCat);

        if (step1.isEmpty()) {
            return new BigDecimal("-1.00");
        }
        String tableName = requireSafeTableName(step1.get(0));

        List<BigDecimal> step2 = jdbcTemplate.query(
                "select fixed_chg from " + tableName + " where tariff = ?",
                (rs, rowNum) -> rs.getBigDecimal("fixed_chg"),
                tariff);

        return step2.isEmpty() ? new BigDecimal("0.00") : step2.get(0);
    }

    private String requireSafeTableName(String tableName) {
        if (tableName == null || !SAFE_TABLE_NAME.matcher(tableName.trim()).matches()) {
            throw new IllegalStateException("etariff_table pointed at an unexpected table name: " + tableName);
        }
        return tableName.trim();
    }
}
