package com.simple.mobile.domain.plans

/** Menu order observed on the authorized handset. Chained codes supplied by the owner;
 * successful single-request execution has NOT been verified with a live purchase. */
data class MobilePlan(val id: String, val option: Int, val data: String, val priceCup: Int,
    val minutes: Int, val sms: Int) {
    val title get() = if (minutes == 0) "Internet · $data" else "Combinado · $data"
    val code get() = "*133*1*4*$option*1#"
    val resources get() = if (minutes == 0) "Solo datos" else "$minutes minutos · $sms SMS"
}
object PlanCatalog {
    const val VERIFIED_ON = "17-11-2026"
    const val SOURCE = "https://www.etecsa.cu/es/movil/planes-personales"
    val plans = listOf(
        MobilePlan("data_4_5gb", 1, "4,5 GB", 240, 0, 0),
        MobilePlan("combo_2gb", 2, "2 GB", 120, 15, 20),
        MobilePlan("combo_4gb", 3, "4 GB", 240, 35, 40),
        MobilePlan("combo_6gb", 4, "6 GB", 360, 60, 70)
    )
    @JvmStatic fun codeFor(id: String): String? = plans.firstOrNull { it.id == id }?.code
}
