package com.hsb.createhsbbill.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One row of the readings + customer data - the Java equivalent of one
 * row in the old Delphi QryRdngs dataset.
 *
 * Source: fsHsb86.pas, method OpenRdngs (the "normal / default" query,
 * lines 471-527):
 *
 *   select *, C.tariff Cst_Trf
 *   from rdngs R, customer C
 *   where R.added_blcy = :adbilcyl
 *   and R.acc_nbr = C.acc_nbr
 *   and C.area_cd = :areacd
 *   order by C.red_code, C.dly_pack, C.wlk_ord
 *
 * One account can have MORE THAN ONE row here - one row per physical
 * meter reading (mtr_type = KWP/KWD/KWO/KVA/KVAH). All the customer
 * fields (name, address, tariff, etc.) repeat identically across every
 * row for the same account, because of the join.
 *
 * mtr_seq groups readings that belong to the SAME physical meter SET at
 * an installation. Most accounts have only one meter set (mtr_seq = 1);
 * an account with more than one physical meter set at the same premises
 * gets one extra bill page per extra set (see NbrofMtrSet /
 * NbrofSheetsPerArea in BillPrint.pas). This is the one point in this
 * translation that is an interpretation of the original data model
 * rather than something spelled out directly in BillPrint.pas - flagged
 * for madam to confirm.
 */
public class ReadingRow {

    private String accNbr;
    private String name;
    private String addressL1;
    private String addressL2;
    private String city;
    private String redCode;
    private String dlyPack;
    private String wlkOrd;
    private String cstTrf;          // C.tariff, aliased Cst_Trf in the query
    private Double cntrDmnd;
    private LocalDate rdngDate;
    private LocalDate prvDate;
    private Integer mtrSeq;
    private String mtrType;         // 'KWP','KWD','KWO','KVA','KVAH'
    private String mtrNbr;
    private Integer rdn;
    private Integer prvRdn;
    private Integer units;
    private Double mFactor;
    private Double computedChg;
    private BigDecimal rate;
    private String cusCat;          // 'B' = business/bulk, 'O' = other, etc.
    private String gstApl;          // 'Y' / 'N'
    private String instId;
    private BigDecimal totSecDep;
    private String invoiceNo;       // only populated in "interactive" mode

    public String getAccNbr() {
        return accNbr;
    }

    public void setAccNbr(String accNbr) {
        this.accNbr = accNbr;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAddressL1() {
        return addressL1;
    }

    public void setAddressL1(String addressL1) {
        this.addressL1 = addressL1;
    }

    public String getAddressL2() {
        return addressL2;
    }

    public void setAddressL2(String addressL2) {
        this.addressL2 = addressL2;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getRedCode() {
        return redCode;
    }

    public void setRedCode(String redCode) {
        this.redCode = redCode;
    }

    public String getDlyPack() {
        return dlyPack;
    }

    public void setDlyPack(String dlyPack) {
        this.dlyPack = dlyPack;
    }

    public String getWlkOrd() {
        return wlkOrd;
    }

    public void setWlkOrd(String wlkOrd) {
        this.wlkOrd = wlkOrd;
    }

    public String getCstTrf() {
        return cstTrf;
    }

    public void setCstTrf(String cstTrf) {
        this.cstTrf = cstTrf;
    }

    public Double getCntrDmnd() {
        return cntrDmnd;
    }

    public void setCntrDmnd(Double cntrDmnd) {
        this.cntrDmnd = cntrDmnd;
    }

    public LocalDate getRdngDate() {
        return rdngDate;
    }

    public void setRdngDate(LocalDate rdngDate) {
        this.rdngDate = rdngDate;
    }

    public LocalDate getPrvDate() {
        return prvDate;
    }

    public void setPrvDate(LocalDate prvDate) {
        this.prvDate = prvDate;
    }

    public Integer getMtrSeq() {
        return mtrSeq;
    }

    public void setMtrSeq(Integer mtrSeq) {
        this.mtrSeq = mtrSeq;
    }

    public String getMtrType() {
        return mtrType;
    }

    public void setMtrType(String mtrType) {
        this.mtrType = mtrType;
    }

    public String getMtrNbr() {
        return mtrNbr;
    }

    public void setMtrNbr(String mtrNbr) {
        this.mtrNbr = mtrNbr;
    }

    public Integer getRdn() {
        return rdn;
    }

    public void setRdn(Integer rdn) {
        this.rdn = rdn;
    }

    public Integer getPrvRdn() {
        return prvRdn;
    }

    public void setPrvRdn(Integer prvRdn) {
        this.prvRdn = prvRdn;
    }

    public Integer getUnits() {
        return units;
    }

    public void setUnits(Integer units) {
        this.units = units;
    }

    public Double getMFactor() {
        return mFactor;
    }

    public void setMFactor(Double mFactor) {
        this.mFactor = mFactor;
    }

    public Double getComputedChg() {
        return computedChg;
    }

    public void setComputedChg(Double computedChg) {
        this.computedChg = computedChg;
    }

    public BigDecimal getRate() {
        return rate;
    }

    public void setRate(BigDecimal rate) {
        this.rate = rate;
    }

    public String getCusCat() {
        return cusCat;
    }

    public void setCusCat(String cusCat) {
        this.cusCat = cusCat;
    }

    public String getGstApl() {
        return gstApl;
    }

    public void setGstApl(String gstApl) {
        this.gstApl = gstApl;
    }

    public String getInstId() {
        return instId;
    }

    public void setInstId(String instId) {
        this.instId = instId;
    }

    public BigDecimal getTotSecDep() {
        return totSecDep;
    }

    public void setTotSecDep(BigDecimal totSecDep) {
        this.totSecDep = totSecDep;
    }

    public String getInvoiceNo() {
        return invoiceNo;
    }

    public void setInvoiceNo(String invoiceNo) {
        this.invoiceNo = invoiceNo;
    }
}
