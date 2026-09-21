package com.hsb.createhsbbill.util;

import java.time.LocalDate;
import java.time.Year;
import java.util.List;

/**
 * The 3 external "pure logic" functions BillPrint.pas depends on - none of
 * these touch a database. All three are defined in FsHsbG0.pas.
 *
 * Two real defects were found in the original Delphi versions of these
 * functions (documented in "The External Seven" reference). Both are
 * FIXED here rather than copied forward:
 *   1. ExtractBillMonthNum silently returned 0 on a bad month name.
 *      Here it throws a clear error instead.
 *   2. NbrofDays received the year in a type that only holds 0-255,
 *      silently corrupting it (e.g. 2010 became 218). Here the real,
     *  full year is used.
 */
public final class BillDateUtils {

    /**
     * Several tables store the bill cycle as text, not a number -
     * confirmed directly against the real database schema:
     *   rdngs.added_blcy    char(3)
     *   transac.proc_blcy   char(3)
     *   mon_tot.bill_cycle  char(3)
     * (while others, like customer.bill_cycle and pay_cyc_update.crnt_cycle,
     * are a real smallint). Every real bill cycle seen so far is a 3-digit
     * number (e.g. 368, 454), so this pads with leading zeros to match the
     * column's exact width, rather than assume it is always already 3 digits.
     */
    public static String billCycleAsChar3(int billCycle) {
        return String.format("%03d", billCycle);
    }

    private static final List<String> MONTH_NAMES = List.of(
            "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec");

    private BillDateUtils() {
    }

    /**
     * FsHsbG0.pas, ExtractBillMonthName (line 498): the 3-letter month
     * name is always characters 6-8 of the "bill month" text, e.g.
     * "2010 Nov" -> "Nov".
     */
    public static String extractBillMonthName(String billMonth) {
        if (billMonth == null || billMonth.length() < 8) {
            throw new IllegalArgumentException("Bill month text is too short to read a month from: " + billMonth);
        }
        // Delphi copy(billmnth, 6, 3) is 1-based: characters 6,7,8.
        // In Java (0-based) that is index 5 up to (but not including) 8.
        return billMonth.substring(5, 8);
    }

    /**
     * FsHsbG0.pas, ExtractBillMonthNum (line 503): turns the 3-letter
     * month name into a number 1-12.
     *
     * FIXED: the original silently returned 0 if nothing matched. This
     * version throws a clear error instead, so a bad value is never
     * allowed to quietly become a wrong date further down the line.
     */
    public static int extractBillMonthNum(String billMonth) {
        String monthName = extractBillMonthName(billMonth);
        int index = MONTH_NAMES.indexOf(monthName);
        if (index < 0) {
            throw new IllegalArgumentException(
                    "Could not recognise month name '" + monthName + "' from bill month text '" + billMonth + "'");
        }
        return index + 1;
    }

    /**
     * FsHsbG0.pas, NbrofDays (line 488): the number of days in a given
     * month of a given year.
     *
     * FIXED: the original parameter type only held 0-255, so a real year
     * like 2010 was silently corrupted to 218 before the leap-year check
     * ran. This version uses the real, full year.
     */
    public static int nbrofDays(int year, int month) {
        switch (month) {
            case 1: case 3: case 5: case 7: case 8: case 10: case 12:
                return 31;
            case 4: case 6: case 9: case 11:
                return 30;
            case 2:
                return Year.isLeap(year) ? 29 : 28;
            default:
                throw new IllegalArgumentException("Not a valid month number: " + month);
        }
    }

    /**
     * BillPrint.pas, IniPrintVal (lines 595-599): combines the three
     * functions above to build the exact tariff-lookup date for a bill
     * cycle, from the "bill month" text (e.g. "2010 Nov" -> 2010-11-30).
     */
    public static LocalDate toTrifDate(String billMonth) {
        int year = Integer.parseInt(billMonth.substring(0, 4));
        int month = extractBillMonthNum(billMonth);
        int day = nbrofDays(year, month);
        return LocalDate.of(year, month, day);
    }
}
