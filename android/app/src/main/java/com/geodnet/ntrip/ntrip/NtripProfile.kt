package com.geodnet.ntrip.ntrip

/** A named, saveable snapshot of [NtripConfig] -- see `data/NtripProfileRepository.kt` for
 * persistence and `ui/NtripScreen.kt`'s "Ntrip Profiles" card for add/load/update/delete. */
data class NtripProfile(
    val id: String,
    val name: String,
    val config: NtripConfig,
)
