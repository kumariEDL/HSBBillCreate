package com.hsb.createhsbbill.repository;

import com.hsb.createhsbbill.model.BillPrintFieldRow;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.regex.Pattern;

/**
 * Reads and writes the "area(code)cycle(cycle)prt" table.
 *
 * IMPORTANT: this table's name is DIFFERENT on every request - it depends
 * on the area code and bill cycle (e.g. area55cycle454prt, area01cycle368prt).
 * A normal Spring Data JPA repository cannot be used here, because JPA
 * needs one FIXED table name, decided in advance. So this class builds the
 * table name itself and runs plain SQL through JdbcTemplate instead - this
 * is the standard, safe way to handle a table whose name changes at runtime.
 *
 * Equivalent of: TBillPrint.Insert_data_bill_prt (BillPrint.pas, line 2165).
 */
@Repository
public class BillPrintFieldRepository {

    /**
     * A table name can never be a "?" placeholder in SQL - the database
     * driver only lets you parameterise VALUES, not table or column names.
     * So the area code has to be joined into the SQL text directly, which
     * means it MUST be checked first, or a bad area code could be used to
     * run something other than a simple insert (SQL injection).
     * Only letters and digits are allowed, 1 to 10 characters.
     */
    private static final Pattern SAFE_AREA_CODE = Pattern.compile("^[A-Za-z0-9]{1,10}$");

    /**
     * The full table name is checked again here, right before it is used,
     * as a second safety net - independent of whichever caller built it.
     */
    private static final Pattern SAFE_TABLE_NAME = Pattern.compile("^area[A-Za-z0-9]{1,10}cycle\\d{1,10}prt$");

    private final JdbcTemplate jdbcTemplate;

    public BillPrintFieldRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Builds the table name the same way BillPrint.pas does
     * (BillPrint.pas line 642 / 2174):
     *   'Area' + gAreaCode + 'cycle' + IntToStr(gBillCycle) + 'prt'
     * Informix is not case-sensitive about table names, and the real table
     * we tested against came back as all lower-case (area01cycle368prt),
     * so this always builds the name in lower case to match exactly.
     */
    public String tableNameFor(String areaCode, int billCycle) {
        if (areaCode == null || !SAFE_AREA_CODE.matcher(areaCode).matches()) {
            throw new IllegalArgumentException("Invalid area code: " + areaCode);
        }
        if (billCycle < 0) {
            throw new IllegalArgumentException("Invalid bill cycle: " + billCycle);
        }
        return "area" + areaCode.toLowerCase() + "cycle" + billCycle + "prt";
    }

    /**
     * Inserts one row into the given area/cycle table - the Java equivalent
     * of one call to Insert_data_bill_prt.
     *
     * @param tableName the exact table name, e.g. "area01cycle368prt" -
     *                  normally produced by tableNameFor(...) above
     * @param row       the 16 values to insert
     */
    public void insert(String tableName, BillPrintFieldRow row) {
        if (tableName == null || !SAFE_TABLE_NAME.matcher(tableName).matches()) {
            throw new IllegalArgumentException("Invalid table name: " + tableName);
        }

        String sql = "insert into " + tableName + " ("
                + "acc_nbr, report_type, bill_cycle, attri_name1, page_no, paragraph, "
                + "row_order, col_order, font_type, font_size, font_style, data_type, "
                + "char_val, int_val, flot_val, date_val"
                + ") values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        jdbcTemplate.update(sql,
                row.getAccNbr(),
                row.getReportType(),
                row.getBillCycle(),
                row.getAttriName1(),
                row.getPageNo(),
                row.getParagraph(),
                row.getRowOrder(),
                row.getColOrder(),
                row.getFontType(),
                row.getFontSize(),
                row.getFontStyle(),
                row.getDataType(),
                row.getCharVal(),
                row.getIntVal(),
                row.getFlotVal(),
                row.getDateVal());
    }
}
