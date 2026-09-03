package com.gilespii.radioex


data class TrackInfo(
    val title: String,      // Naziv pesme
    val artist: String,     // Ime izvođača (NOVO POLJE)
    val imageUrl: String? = null // Slika
) {
    companion object {
        fun empty() = TrackInfo("", "", null)
        fun fallback(name: String, id: String = "") = TrackInfo(name, id, null)
    }
}
