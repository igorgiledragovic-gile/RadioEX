package com.gilespii.radioex

enum class MetadataType {
    AUTO,           // Automatski detektuje tip
    STANDARD,       // ICY metadata (Shoutcast/Icecast stream)
    JSON_NAXI,      // Naxi Firebase API
    JSON_RADIOS,    // Radio S JSON API
    SHOUTCAST_TEXT, // HTML parsing (Radio TRI, Shoutcast admin page)
    WEBSOCKET_BALKAN
}
