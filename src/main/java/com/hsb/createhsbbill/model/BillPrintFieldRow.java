package com.hsb.createhsbbill.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One row of the "area(code)cycle(cycle)prt" table.
 *
 * This is the Java shape of every single call the old Delphi program made
 * to Insert_data_bill_prt (BillPrint.pas, line 2165). Each row is ONE
 * printable value on the bill: what it is, where it goes on the page, and
 * how it should look.
 *
 * Field types below match the REAL database table exactly - confirmed by
 * connecting to the live test database and reading the actual column
 * definitions of area01cycle368prt on 2026-09-15:
 *
 *   acc_nbr       char(10)
 *   report_type   char(8)
 *   bill_cycle    smallint
 *   attri_name1   char(30)
 *   page_no       smallint
 *   paragraph     decimal(8,2)
 *   row_order     decimal(8,2)
 *   col_order     decimal(8,2)
 *   font_type     char(15)
 *   font_size     char(10)
 *   font_style    char(15)
 *   data_type     char(1)   - 'C' text, 'N' whole number, 'F' decimal number, 'D' date
 *   char_val      char(100)
 *   int_val       integer
 *   flot_val      decimal(15,2)
 *   date_val      date
 */
public class BillPrintFieldRow {

    private String accNbr;
    private String reportType;
    private Integer billCycle;
    private String attriName1;
    private Integer pageNo;
    private BigDecimal paragraph;
    private BigDecimal rowOrder;
    private BigDecimal colOrder;
    private String fontType;
    private String fontSize;
    private String fontStyle;
    private String dataType;
    private String charVal;
    private Integer intVal;
    private BigDecimal flotVal;
    private LocalDate dateVal;

    public BillPrintFieldRow() {
        // Spring/Jackson need an empty constructor to build this object
        // from JSON, and JdbcTemplate code below fills it in field by field.
    }

    public String getAccNbr() {
        return accNbr;
    }

    public void setAccNbr(String accNbr) {
        this.accNbr = accNbr;
    }

    public String getReportType() {
        return reportType;
    }

    public void setReportType(String reportType) {
        this.reportType = reportType;
    }

    public Integer getBillCycle() {
        return billCycle;
    }

    public void setBillCycle(Integer billCycle) {
        this.billCycle = billCycle;
    }

    public String getAttriName1() {
        return attriName1;
    }

    public void setAttriName1(String attriName1) {
        this.attriName1 = attriName1;
    }

    public Integer getPageNo() {
        return pageNo;
    }

    public void setPageNo(Integer pageNo) {
        this.pageNo = pageNo;
    }

    public BigDecimal getParagraph() {
        return paragraph;
    }

    public void setParagraph(BigDecimal paragraph) {
        this.paragraph = paragraph;
    }

    public BigDecimal getRowOrder() {
        return rowOrder;
    }

    public void setRowOrder(BigDecimal rowOrder) {
        this.rowOrder = rowOrder;
    }

    public BigDecimal getColOrder() {
        return colOrder;
    }

    public void setColOrder(BigDecimal colOrder) {
        this.colOrder = colOrder;
    }

    public String getFontType() {
        return fontType;
    }

    public void setFontType(String fontType) {
        this.fontType = fontType;
    }

    public String getFontSize() {
        return fontSize;
    }

    public void setFontSize(String fontSize) {
        this.fontSize = fontSize;
    }

    public String getFontStyle() {
        return fontStyle;
    }

    public void setFontStyle(String fontStyle) {
        this.fontStyle = fontStyle;
    }

    public String getDataType() {
        return dataType;
    }

    public void setDataType(String dataType) {
        this.dataType = dataType;
    }

    public String getCharVal() {
        return charVal;
    }

    public void setCharVal(String charVal) {
        this.charVal = charVal;
    }

    public Integer getIntVal() {
        return intVal;
    }

    public void setIntVal(Integer intVal) {
        this.intVal = intVal;
    }

    public BigDecimal getFlotVal() {
        return flotVal;
    }

    public void setFlotVal(BigDecimal flotVal) {
        this.flotVal = flotVal;
    }

    public LocalDate getDateVal() {
        return dateVal;
    }

    public void setDateVal(LocalDate dateVal) {
        this.dateVal = dateVal;
    }
}
