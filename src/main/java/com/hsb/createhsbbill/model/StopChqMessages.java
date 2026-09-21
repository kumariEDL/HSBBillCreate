package com.hsb.createhsbbill.model;

/**
 * BillPrint.pas, GetStopChqMsg (line 1894): the 4 lines of warning text
 * printed on bills flagged for stop-cheque. All blank if the bill cycle
 * has no configured message.
 */
public record StopChqMessages(String line1, String line2, String line3, String line4) {

    public static StopChqMessages blank() {
        return new StopChqMessages("", "", "", "");
    }
}
