package com.geodnet.ntrip.location

data class MockLocationState(
    val enabled: Boolean = false,
    val updateCount: Long = 0,
    val lastFix: PositionFix? = null,
    val errorMessage: String? = null,
)
