package com.gilespii.radioex

import java.io.Serializable

data class RadioStation(
    val id: Int,
    val name: String,
    val imageResId: Int,
    val streamUrl: String,
    val metadataUrl: String = "",
    val metadataType: MetadataType = MetadataType.AUTO,
    var isFavorite: Boolean = false,
    val kbps: Int = 128
) : Serializable

fun RadioStation.getCategoryId(): String = when {
    id in 3..32 || id == 97 -> "naxi"          // Naxi stanice + Radio TRI
    id in 33..69 -> "radios"                   // Radio S stanice  
    id == 1 || id == 96 || id in 70..81 -> "hitfm"  // TDI + Karolina + Hit FM stanice
    id in 82..89 -> "scg"                      // RSG (Radio Srbija Grad) stanice
    id in 90..95 -> "play"                     // Play Radio stanice
    else -> "ostalo"                           // Balkan Dance (ID 2)
}
