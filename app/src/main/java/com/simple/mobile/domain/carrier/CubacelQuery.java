package com.simple.mobile.domain.carrier;

/** Read-only resource queries verified against ETECSA on 2026-11-09.
 * No purchase, activation, transfer or identity-query commands belong here.
 */
public enum CubacelQuery {
    BALANCE("*222#", "https://www.etecsa.cu/es/movil/planes-personales"),
    DATA("*222*328#", "https://www.etecsa.cu/es/movil/planes-personales"),
    BONUSES_AND_USD_PLANS("*222*266#", "https://www.etecsa.cu/es/movil/planes-personales"),
    VOICE("*222*869#", "https://www.etecsa.cu/es/movil/planes-personales"),
    SMS("*222*767#", "https://www.etecsa.cu/es/movil/planes-personales"),
    RECHARGE_STATUS("*222*732#", "https://www.etecsa.cu/es/movil/necesitas-saldo");

    private final String code;
    private final String source;
    CubacelQuery(String code, String source) { this.code = code; this.source = source; }
    public String code() { return code; }
    public String source() { return source; }
}
