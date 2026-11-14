package com.simple.mobile.presentation

import com.simple.mobile.domain.carrier.CubacelQuery
import org.junit.Assert.*
import org.junit.Test

class QueryLoadingTest {
    @Test fun loadingBelongsOnlyToTheActiveCategoryAndOriginalSim() {
        val state = DashboardState(busy = true, selected = 4, querySubscription = 4, activeQuery = CubacelQuery.DATA)
        assertTrue(state.isQuerying(CubacelQuery.DATA))
        assertFalse(state.isQuerying(CubacelQuery.BALANCE))
        assertFalse(state.copy(selected = 9).isQuerying(CubacelQuery.DATA))
        assertFalse(state.copy(busy = false).isQuerying(CubacelQuery.DATA))
    }
}
