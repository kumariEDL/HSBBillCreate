package com.hsb.createhsbbill.service;

import com.hsb.createhsbbill.model.BillPrintFieldRow;
import com.hsb.createhsbbill.repository.BillPrintFieldRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * The createhsbbill API's main entry point: calculates the real bill data
 * for an area + bill cycle (BillCalculationService), saves every row into
 * the real area(code)cycle(cycle)prt table, and returns the same rows so
 * the caller can see exactly what was written - matching what madam asked
 * for: "insert AND return".
 *
 * Part 1's hardcoded test row has been fully replaced - nothing here is
 * made up any more.
 */
@Service
public class BillPrintService {

    private final BillCalculationService billCalculationService;
    private final BillPrintFieldRepository repository;

    public BillPrintService(BillCalculationService billCalculationService,
                             BillPrintFieldRepository repository) {
        this.billCalculationService = billCalculationService;
        this.repository = repository;
    }

    /**
     * Calculates and saves the whole bill for one area + bill cycle -
     * every account, every page, every field.
     */
    public List<BillPrintFieldRow> generateAndSaveBill(String areaCode, int billCycle) {
        List<BillPrintFieldRow> rows = billCalculationService.generateBill(areaCode, billCycle);
        String tableName = repository.tableNameFor(areaCode, billCycle);
        for (BillPrintFieldRow row : rows) {
            repository.insert(tableName, row);
        }
        return rows;
    }
}
