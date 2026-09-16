package com.hsb.createhsbbill.service;

import com.hsb.createhsbbill.model.BillPrintFieldRow;
import com.hsb.createhsbbill.repository.BillPrintFieldRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * PART 1 ONLY.
 *
 * This does not calculate any real bill data yet - it does not read
 * customer names, meter readings, or charges. Its only job right now is
 * to prove that the API can build the correct table name and insert one
 * row into it correctly.
 *
 * Once this part is confirmed working, later parts will replace
 * insertSampleRow(...) with the real calculation, one piece at a time -
 * following the same call chain as TBillPrint.bill_pint_from_table and
 * its setup steps (IniPrintVal, NbrofSheetsPerArea, etc.).
 */
@Service
public class BillPrintService {

    private final BillPrintFieldRepository repository;

    public BillPrintService(BillPrintFieldRepository repository) {
        this.repository = repository;
    }

    /**
     * Inserts one clearly-marked test row for the given area and bill
     * cycle, and returns it so the caller can see exactly what was written.
     */
    public BillPrintFieldRow insertSampleRow(String areaCode, int billCycle) {
        String tableName = repository.tableNameFor(areaCode, billCycle);

        BillPrintFieldRow row = new BillPrintFieldRow();
        row.setAccNbr("0000000000");
        // "TESTAPI" instead of the old code's "Priyanka" - marks this as a
        // Part 1 test row, so it can be found and removed later without
        // touching any of the real bill data already in the table.
        row.setReportType("TESTAPI");
        row.setBillCycle(billCycle);
        row.setAttriName1("");
        row.setPageNo(1);
        row.setParagraph(new BigDecimal("0.00"));
        row.setRowOrder(new BigDecimal("0.00"));
        row.setColOrder(new BigDecimal("0.00"));
        row.setFontType("Courier New");
        row.setFontSize("10");
        row.setFontStyle("Regular");
        row.setDataType("C");
        row.setCharVal("createhsbbill Part 1 test row");
        row.setIntVal(0);
        row.setFlotVal(new BigDecimal("0.00"));
        row.setDateVal(null);

        repository.insert(tableName, row);
        return row;
    }
}
