package com.hsb.createhsbbill.repository;

import com.hsb.createhsbbill.model.TransactionRow;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * BillPrint.pas, InsertTxnData + OpenTxnData (lines 1958-2040) - copies
 * one account's transaction and payment history into one common shape,
 * then reads it back in date order.
 *
 * The original did this by writing into a temporary scratch table,
 * PAYMENT_&lt;area&gt;, then reading it straight back out. That table was
 * confirmed to be disposable, working storage only (see the
 * "PAYMENT_&lt;area&gt; investigation" notes) - so this version does the
 * same reshaping entirely in memory, with nothing written to any table.
 */
@Repository
public class TransactionRepository {

    private final JdbcTemplate jdbcTemplate;

    public TransactionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Loads and combines this account's transactions and payments for
     * the given bill cycle, in date order - the equivalent of calling
     * InsertTxnData followed by OpenTxnData for one account.
     *
     * @param seekDesc used to look up each transaction's description text
     *                 (ReferenceDataRepository.seekDesc), passed in rather
     *                 than called directly here to keep this class
     *                 focused on reading transac/payment only.
     */
    public List<TransactionRow> loadTransactionsAndPayments(
            String accNbr, int billCycle, java.util.function.Function<String, String> seekDesc) {

        List<TransactionRow> rows = new ArrayList<>();

        // transac.proc_blcy is char(3), confirmed against the real schema -
        // text, not a number (unlike payment.pro_blcy below, which is a
        // real smallint).
        String billCycleText = com.hsb.createhsbbill.util.BillDateUtils.billCycleAsChar3(billCycle);

        // Informix's JDBC driver does not correctly support
        // getObject(column, LocalDate/LocalDateTime.class) - confirmed by a
        // real ClassCastException during testing - so getTimestamp()/
        // getDate() are used instead, which work with it.
        //
        // txn_dtime is a real datetime column; actl_pay_date is a plain
        // date column (no time part), so it is read as a date and placed
        // at the start of that day for a consistent LocalDateTime type.

        // Pass 1 (line 1964): transac rows with a non-zero amount.
        rows.addAll(jdbcTemplate.query(
                "select txn_type, txn_amt, amt_stat, txn_dtime from transac "
                        + "where acc_nbr = ? and proc_blcy = ? and txn_amt > 0",
                (rs, rowNum) -> new TransactionRow(
                        accNbr,
                        billCycle,
                        rs.getString("txn_type"),
                        seekDesc.apply(rs.getString("txn_type")),
                        rs.getDouble("txn_amt"),
                        rs.getString("amt_stat"),
                        rs.getTimestamp("txn_dtime").toLocalDateTime()),
                accNbr, billCycleText));

        // Pass 2 (line 1998): payment rows, always described as "Payment".
        rows.addAll(jdbcTemplate.query(
                "select paid_amt, actl_pay_date from payment where acc_nbr = ? and pro_blcy = ?",
                (rs, rowNum) -> new TransactionRow(
                        accNbr,
                        billCycle,
                        "PAYM",
                        "Payment",
                        rs.getDouble("paid_amt"),
                        "-1",
                        rs.getDate("actl_pay_date").toLocalDate().atStartOfDay()),
                accNbr, billCycle));

        // OpenTxnData (line 2036): "order by tran_date"
        rows.sort(Comparator.comparing(TransactionRow::getTranDate));
        return rows;
    }
}
