package com.hsb.createhsbbill.repository;

import com.hsb.createhsbbill.model.ReadingRow;
import com.hsb.createhsbbill.util.BillDateUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * The main readings + customer data source - the Java equivalent of the
 * "normal / default" QryRdngs query, found in fsHsb86.pas, method
 * OpenRdngs (lines 471-527), and confirmed against the real database's
 * .dfm-stored design-time SQL:
 *
 *   select *, C.tariff Cst_Trf
 *   from rdngs R, customer C
 *   where R.added_blcy = :adbilcyl
 *   and R.acc_nbr = C.acc_nbr
 *   and C.area_cd = :areacd
 *   order by C.red_code, C.dly_pack, C.wlk_ord
 *
 * Written here as an explicit column list (rather than "select *"), which
 * is both clearer and safer than the original - but reads exactly the
 * same rows, in exactly the same order.
 */
@Repository
public class ReadingRepository {

    private final JdbcTemplate jdbcTemplate;

    public ReadingRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ReadingRow> loadReadings(String areaCode, int billCycle) {
        // rdngs.added_blcy is char(3), confirmed against the real schema -
        // text, not a number.
        String billCycleText = BillDateUtils.billCycleAsChar3(billCycle);

        return jdbcTemplate.query(
                "select R.acc_nbr, C.name, C.address_l1, C.address_l2, C.city, "
                        + "C.red_code, C.dly_pack, C.wlk_ord, C.tariff as cst_trf, C.cntr_dmnd, "
                        + "R.rdng_date, R.prv_date, R.mtr_seq, R.mtr_type, R.mtr_nbr, "
                        + "R.rdn, R.prv_rdn, R.units, R.m_factor, R.computed_chg, R.rate, "
                        + "C.cus_cat, C.gst_apl, C.inst_id, C.tot_sec_dep "
                        + "from rdngs R, customer C "
                        + "where R.added_blcy = ? and R.acc_nbr = C.acc_nbr and C.area_cd = ? "
                        + "order by C.red_code, C.dly_pack, C.wlk_ord",
                (rs, rowNum) -> {
                    ReadingRow row = new ReadingRow();
                    row.setAccNbr(rs.getString("acc_nbr"));
                    row.setName(rs.getString("name"));
                    row.setAddressL1(rs.getString("address_l1"));
                    row.setAddressL2(rs.getString("address_l2"));
                    row.setCity(rs.getString("city"));
                    row.setRedCode(rs.getString("red_code"));
                    row.setDlyPack(rs.getString("dly_pack"));
                    row.setWlkOrd(rs.getString("wlk_ord"));
                    row.setCstTrf(rs.getString("cst_trf"));
                    row.setCntrDmnd(rs.getObject("cntr_dmnd") == null ? null : rs.getDouble("cntr_dmnd"));
                    // Informix's JDBC driver does not correctly support
                    // getObject(column, LocalDate.class) - confirmed by a
                    // real ClassCastException during testing - so getDate()
                    // + toLocalDate() is used instead, which works with it.
                    java.sql.Date rdngDate = rs.getDate("rdng_date");
                    row.setRdngDate(rdngDate == null ? null : rdngDate.toLocalDate());
                    java.sql.Date prvDate = rs.getDate("prv_date");
                    row.setPrvDate(prvDate == null ? null : prvDate.toLocalDate());
                    row.setMtrSeq(rs.getInt("mtr_seq"));
                    row.setMtrType(rs.getString("mtr_type"));
                    row.setMtrNbr(rs.getString("mtr_nbr"));
                    row.setRdn(rs.getInt("rdn"));
                    row.setPrvRdn(rs.getInt("prv_rdn"));
                    row.setUnits(rs.getInt("units"));
                    row.setMFactor(rs.getObject("m_factor") == null ? null : rs.getDouble("m_factor"));
                    row.setComputedChg(rs.getObject("computed_chg") == null ? null : rs.getDouble("computed_chg"));
                    row.setRate(rs.getBigDecimal("rate"));
                    row.setCusCat(rs.getString("cus_cat"));
                    row.setGstApl(rs.getString("gst_apl"));
                    row.setInstId(rs.getString("inst_id"));
                    row.setTotSecDep(rs.getBigDecimal("tot_sec_dep"));
                    return row;
                },
                billCycleText, areaCode);
    }
}
