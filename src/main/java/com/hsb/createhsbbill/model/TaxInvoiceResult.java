package com.hsb.createhsbbill.model;

/**
 * BillPrint.pas, Get_Tax_Inv (line 434): is this a tax invoice, and if so,
 * what is the VAT registration number to print?
 */
public record TaxInvoiceResult(boolean taxInvoice, String taxNumber) {
}
