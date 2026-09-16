package com.hsb.createhsbbill.controller;

import com.hsb.createhsbbill.model.BillPrintFieldRow;
import com.hsb.createhsbbill.service.BillPrintService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The createhsbbill API - PART 1.
 *
 * Right now this only proves one row can be inserted correctly into the
 * real area(code)cycle(cycle)prt table. It does not yet build a real bill -
 * that is added on top of this, part by part, once this part is confirmed
 * to be correct.
 */
@RestController
@RequestMapping("/createhsbbill")
public class BillPrintController {

    private final BillPrintService billPrintService;

    public BillPrintController(BillPrintService billPrintService) {
        this.billPrintService = billPrintService;
    }

    /**
     * Example call, once deployed:
     *   POST /createhsbbill/createhsbbill?areaCode=01&billCycle=368
     *
     * areaCode is kept as text (not a number) because area codes can have
     * a leading zero, e.g. "01" - exactly as gAreaCode is a string in the
     * old Delphi code (Global.pas).
     */
    @PostMapping
    public BillPrintFieldRow createHsbBill(
            @RequestParam String areaCode,
            @RequestParam int billCycle) {
        return billPrintService.insertSampleRow(areaCode, billCycle);
    }
}
