package com.gilespii.radioex

object RadioRepository {
    fun getStations(): ArrayList<RadioStation> {
        val stations = ArrayList<RadioStation>()

// 1. GLAVNE
        stations.add(RadioStation(1, "TDI Radio", R.drawable.logo_tdi, "http://streaming.tdiradio.com:8000/tdiradio", "", MetadataType.AUTO))
        stations.add(RadioStation(2, "Radio Balkan Dance", R.drawable.logo_radio_balkan_dance, "https://radiobalkan.live/radio/live/dance.mp3", "", MetadataType.WEBSOCKET_BALKAN))

// 2. NAXI
        stations.add(RadioStation(3, "Naxi Mix", R.drawable.logo_naxi_mix, "http://naxidigital-mix128.streaming.rs:8220/;stream.nsv", "mix", MetadataType.JSON_NAXI))
        stations.add(RadioStation(4, "Naxi Ex Yu Rock", R.drawable.logo_ex_yu_rock, "http://naxidigital-exyurock128.streaming.rs:8400/;stream.nsv", "exyurock", MetadataType.JSON_NAXI))
        stations.add(RadioStation(5, "Naxi Cafe", R.drawable.logo_naxi_cafe, "https://naxidigital-cafe128ssl.streaming.rs:8022/;stream.nsv", "cafe", MetadataType.JSON_NAXI))
        stations.add(RadioStation(6, "Naxi 80s", R.drawable.logo_naxi_80e, "https://naxidigital-80s128ssl.streaming.rs:8042/;stream.nsv", "80e", MetadataType.JSON_NAXI))
        stations.add(RadioStation(7, "Naxi 90s", R.drawable.logo_naxi_90e, "https://naxidigital-90s128ssl.streaming.rs:8282/;stream.nsv", "90e", MetadataType.JSON_NAXI))
        stations.add(RadioStation(8, "Naxi 70s", R.drawable.logo_naxi_70e, "https://naxidigital-70s128ssl.streaming.rs:8382/;stream.nsv", "70e", MetadataType.JSON_NAXI))
        stations.add(RadioStation(9, "Naxi ExYu", R.drawable.logo_naxi_ex_yu, "https://naxidigital-exyu128ssl.streaming.rs:8242/;stream.nsv", "exyu", MetadataType.JSON_NAXI))
        stations.add(RadioStation(10, "Naxi House", R.drawable.logo_naxi_house, "https://naxidigital-house128ssl.streaming.rs:8002/;stream.nsv", "house", MetadataType.JSON_NAXI))
        stations.add(RadioStation(11, "Naxi Jazz", R.drawable.logo_naxi_jazz, "https://naxidigital-jazz128ssl.streaming.rs:8172/;stream.nsv", "jazz", MetadataType.JSON_NAXI))
        stations.add(RadioStation(12, "Naxi Love", R.drawable.logo_naxi_love, "https://naxidigital-love128ssl.streaming.rs:8102/;stream.nsv", "love", MetadataType.JSON_NAXI))
        stations.add(RadioStation(13, "Naxi Dance", R.drawable.logo_naxi_dance, "https://naxidigital-dance128ssl.streaming.rs:8112/;stream.nsv", "dance", MetadataType.JSON_NAXI))
        stations.add(RadioStation(14, "Naxi Evergreen", R.drawable.logo_naxi_evergreen, "https://naxidigital-evergreen128ssl.streaming.rs:8012/;stream.nsv", "evergreen", MetadataType.JSON_NAXI))
        stations.add(RadioStation(15, "Naxi Fresh", R.drawable.logo_naxi_fresh, "https://naxidigital-fresh128ssl.streaming.rs:8212/;stream.nsv", "fresh", MetadataType.JSON_NAXI))
        stations.add(RadioStation(16, "Naxi Adore", R.drawable.logo_naxi_adore, "https://naxidigital-adore128ssl.streaming.rs:8332/;stream.nsv", "adore", MetadataType.JSON_NAXI))
        stations.add(RadioStation(17, "Naxi Blues", R.drawable.logo_naxi_blues_rock, "https://naxidigital-blues128ssl.streaming.rs:8312/;stream.nsv", "blues-rock", MetadataType.JSON_NAXI))
        stations.add(RadioStation(18, "Naxi Boem", R.drawable.logo_naxi_boem, "https://naxidigital-boem128ssl.streaming.rs:8162/;stream.nsv", "boem", MetadataType.JSON_NAXI))
        stations.add(RadioStation(19, "Naxi Chillwave", R.drawable.logo_naxi_chillwave, "https://naxidigital-chillwave128ssl.streaming.rs:8322/;stream.nsv", "chillwave", MetadataType.JSON_NAXI))
        stations.add(RadioStation(20, "Naxi Classic", R.drawable.logo_naxi_classic, "https://naxidigital-classic128ssl.streaming.rs:8032/;stream.nsv", "classic", MetadataType.JSON_NAXI))
        stations.add(RadioStation(21, "Naxi Clubbing", R.drawable.logo_naxi_clubbing, "https://naxidigital-clubbing128ssl.streaming.rs:8092/;stream.nsv", "clubbing", MetadataType.JSON_NAXI))
        stations.add(RadioStation(22, "Naxi Fitness", R.drawable.logo_naxi_fitness, "https://naxidigital-fitness128ssl.streaming.rs:8292/;stream.nsv", "fitness", MetadataType.JSON_NAXI))
        stations.add(RadioStation(23, "Naxi Gold", R.drawable.logo_naxi_gold, "https://naxidigital-gold128ssl.streaming.rs:8062/;stream.nsv", "gold", MetadataType.JSON_NAXI))
        stations.add(RadioStation(24, "Naxi Latino", R.drawable.logo_naxi_latino, "https://naxidigital-latino128ssl.streaming.rs:8232/;stream.nsv", "latino", MetadataType.JSON_NAXI))
        stations.add(RadioStation(25, "Naxi Lounge", R.drawable.logo_naxi_lounge, "https://naxidigital-lounge128ssl.streaming.rs:8252/;stream.nsv", "lounge", MetadataType.JSON_NAXI))
        stations.add(RadioStation(26, "Naxi Millennium", R.drawable.logo_naxi_milennium, "https://naxidigital-millennium128ssl.streaming.rs:8342/;stream.nsv", "millennium", MetadataType.JSON_NAXI))
        stations.add(RadioStation(27, "Naxi R'n'B", R.drawable.logo_naxi_rnb, "https://naxidigital-rnb128ssl.streaming.rs:8122/;stream.nsv", "rnb", MetadataType.JSON_NAXI))
        stations.add(RadioStation(28, "Naxi Rock", R.drawable.logo_naxi_rock, "https://naxidigital-rock128ssl.streaming.rs:8182/;stream.nsv", "rock", MetadataType.JSON_NAXI))
        stations.add(RadioStation(29, "Naxi Chill Radio", R.drawable.logo_naxi_chill, "https://naxidigital-chill128ssl.streaming.rs:8412/;stream.nsv", "chill", MetadataType.JSON_NAXI))
        stations.add(RadioStation(30, "Naxi Funk Radio", R.drawable.logo_naxi_funk, "https://naxidigital-funk128ssl.streaming.rs:8362/;stream.nsv", "funk", MetadataType.JSON_NAXI))
        stations.add(RadioStation(31, "Naxi Disco Radio", R.drawable.logo_naxi_disco, "https://naxidigital-disco128ssl.streaming.rs:8352/;stream.nsv", "disco", MetadataType.JSON_NAXI))
        stations.add(RadioStation(32, "Naxi Reggae Radio", R.drawable.logo_naxi_reggae, "https://naxidigital-reggae128.streaming.rs:8422/;stream.nsv", "reggae", MetadataType.JSON_NAXI))

// 3. RADIO S
        stations.add(RadioStation(33, "Radio S1", R.drawable.logo_s1, "http://edge-rs-04.maksnet.tv/asmedia/radios/playlist.m3u8", "https://www.radios.rs/includes/get/now-playing-json.php?radio=s1", MetadataType.JSON_RADIOS))
        stations.add(RadioStation(34, "Radio S2", R.drawable.logo_s2, "http://edge-rs-01.maksnet.tv/asmedia/index/playlist.m3u8", "https://www.radios.rs/includes/get/now-playing-json.php?radio=s2", MetadataType.JSON_RADIOS))
        stations.add(RadioStation(35, "Radio S3", R.drawable.logo_s3, "http://edge-de-04.maksnet.tv/asmedia/pingvin/playlist.m3u8", "https://www.radios.rs/includes/get/now-playing-json.php?radio=s3", MetadataType.JSON_RADIOS))
        stations.add(RadioStation(36, "Radio S4", R.drawable.logo_s4, "http://edge-rs-01.maksnet.tv/asmedia/gradski/playlist.m3u8", "https://www.radios.rs/includes/get/now-playing-json.php?radio=s4", MetadataType.JSON_RADIOS))
        stations.add(RadioStation(37, "Radio S1 CG", R.drawable.logo_s1cg, "http://edge-rs-01.maksnet.tv/asmedia/radios1-cg/playlist.m3u8", "https://www.radios.rs/includes/get/now-playing-json.php?radio=s1_cg", MetadataType.JSON_RADIOS))
        stations.add(RadioStation(38, "Radio S1 BIH", R.drawable.logo_s1bih, "http://edge-rs-01.maksnet.tv/asmedia/radios1-bih/playlist.m3u8", "https://www.radios.rs/includes/get/now-playing-json.php?radio=s1_bih", MetadataType.JSON_RADIOS))
        stations.add(RadioStation(39, "Radio S3 CG", R.drawable.logo_s3cg, "http://edge-de-04.maksnet.tv/asmedia/radios3-cg/playlist.m3u8", "https://www.radios.rs/includes/get/now-playing-json.php?radio=s3_cg", MetadataType.JSON_RADIOS))
        stations.add(RadioStation(40, "Radio S Cafe", R.drawable.logo_s_cafe, "http://edge-rs-04.maksnet.tv/asmedia/radio_s_cafe/playlist.m3u8", "https://www.radios.rs/includes/get/now-playing-json.php?radio=s_cafe", MetadataType.JSON_RADIOS))
        stations.add(RadioStation(41, "Radio S 80-te", R.drawable.logo_s_80e, "http://edge-rs-03.maksnet.tv/asmedia/radio_s_80te/playlist.m3u8", "https://www.radios.rs/includes/get/now-playing-json.php?radio=s_80te", MetadataType.JSON_RADIOS))
        stations.add(RadioStation(42, "Radio S Xtra", R.drawable.logo_s_xtra, "http://edge-rs-05.maksnet.tv/asmedia/radio_s_love/playlist.m3u8", "https://www.radios.rs/includes/get/now-playing-json.php?radio=s_love", MetadataType.JSON_RADIOS))
        stations.add(RadioStation(43, "Radio S Pop&Rock", R.drawable.logo_s_poprock, "http://edge-rs-05.maksnet.tv/asmedia/radio_s_pop_80_90/playlist.m3u8", "https://www.radios.rs/includes/get/now-playing-json.php?radio=s_pop", MetadataType.JSON_RADIOS))
        stations.add(RadioStation(44, "Radio S Ex-Yu", R.drawable.logo_s_exyu, "http://53a7ed211fc32.streamlock.net/asmedia/radio_s_ex_yu/playlist.m3u8", "https://www.radios.rs/includes/get/now-playing-json.php?radio=s_ex_yu", MetadataType.JSON_RADIOS))
        stations.add(RadioStation(45, "Radio S Sport Urban", R.drawable.logo_s_sporturban, "http://edge-rs-05.maksnet.tv/asmedia/s_sport_urban/playlist.m3u8", "https://www.radios.rs/includes/get/now-playing-json.php?radio=s_sport_urban", MetadataType.JSON_RADIOS))
        stations.add(RadioStation(46, "Radio S Folk Stars", R.drawable.logo_s_folkstars, "http://edge-rs-04.maksnet.tv/asmedia/radio_s_lounge/playlist.m3u8", "https://www.radios.rs/includes/get/now-playing-json.php?radio=s_lounge", MetadataType.JSON_RADIOS))
        stations.add(RadioStation(47, "Radio S Juzni", R.drawable.logo_s_juzni, "http://edge-rs-03.maksnet.tv/asmedia/radio_s_juzni/playlist.m3u8", "https://www.radios.rs/includes/get/now-playing-json.php?radio=s_juzni", MetadataType.JSON_RADIOS))
        stations.add(RadioStation(48, "Radio S Trap&Rap", R.drawable.logo_s_traprap, "http://edge-rs-01.maksnet.tv/asmedia/radio_s_mchits/playlist.m3u8", "https://www.radios.rs/includes/get/now-playing-json.php?radio=s_mchits", MetadataType.JSON_RADIOS))
        stations.add(RadioStation(49, "Radio S Classic", R.drawable.logo_s_classic, "http://edge-rs-03.maksnet.tv/asmedia/s_classic/playlist.m3u8", "https://www.radios.rs/includes/get/now-playing-json.php?radio=s_classic", MetadataType.JSON_RADIOS))
        stations.add(RadioStation(50, "Radio S Dance", R.drawable.logo_s_dance, "http://edge-rs-05.maksnet.tv/asmedia/radio_s_energy/playlist.m3u8", "https://www.radios.rs/includes/get/now-playing-json.php?radio=s_energy", MetadataType.JSON_RADIOS))
        stations.add(RadioStation(51, "Radio S Gold", R.drawable.logo_s_gold, "http://edge-rs-01.maksnet.tv/asmedia/radio_s_golds_60_70/playlist.m3u8", "https://www.radios.rs/includes/get/now-playing-json.php?radio=s_gold", MetadataType.JSON_RADIOS))
        stations.add(RadioStation(52, "Radio S Pop Ballads", R.drawable.logo_s_popbalads, "http://edge-rs-04.maksnet.tv/asmedia/s_pop_ballads/playlist.m3u8", "https://www.radios.rs/includes/get/now-playing-json.php?radio=s_pop_ballads", MetadataType.JSON_RADIOS))
        stations.add(RadioStation(53, "Radio S Modern Classic", R.drawable.logo_s_modernclassic, "http://edge-de-04.maksnet.tv/asmedia/s_mod_classic/playlist.m3u8", "https://www.radios.rs/includes/get/now-playing-json.php?radio=s_mod_classic", MetadataType.JSON_RADIOS))
        stations.add(RadioStation(54, "Radio S Latino", R.drawable.logo_s_latino, "http://edge-rs-01.maksnet.tv/asmedia/radio_s_latino/playlist.m3u8", "https://www.radios.rs/includes/get/now-playing-json.php?radio=s_latino", MetadataType.JSON_RADIOS))
        stations.add(RadioStation(55, "Radio S 2000-e", R.drawable.logo_s_2000e, "http://edge-rs-05.maksnet.tv/asmedia/radio_s_2000-e/playlist.m3u8", "https://www.radios.rs/includes/get/now-playing-json.php?radio=s_2000-e", MetadataType.JSON_RADIOS))
        stations.add(RadioStation(56, "Radio S Starogradski", R.drawable.logo_s_starogradski, "http://edge-rs-04.maksnet.tv/asmedia/s_starogradski/playlist.m3u8", "https://www.radios.rs/includes/get/now-playing-json.php?radio=s_starogradski", MetadataType.JSON_RADIOS))
        stations.add(RadioStation(57, "Radio S Chill", R.drawable.logo_s_chill, "http://edge-rs-04.maksnet.tv/asmedia/s_chill/playlist.m3u8", "https://www.radios.rs/includes/get/now-playing-json.php?radio=s_chill", MetadataType.JSON_RADIOS))
        stations.add(RadioStation(58, "Radio S Gym", R.drawable.logo_s_gym, "http://edge-rs-04.maksnet.tv/asmedia/s_gym/playlist.m3u8", "https://www.radios.rs/includes/get/now-playing-json.php?radio=s_gym", MetadataType.JSON_RADIOS))
        stations.add(RadioStation(59, "Radio S Pop Folk", R.drawable.logo_s_folk, "http://edge-rs-03.maksnet.tv/asmedia/radio_s_pop_folk/playlist.m3u8", "https://www.radios.rs/includes/get/now-playing-json.php?radio=s_pop_folk", MetadataType.JSON_RADIOS))
        stations.add(RadioStation(60, "Radio S Rock Ballads", R.drawable.logo_s_rockbalads, "http://edge-rs-04.maksnet.tv/asmedia/s_rock_ballads/playlist.m3u8", "https://www.radios.rs/includes/get/now-playing-json.php?radio=s_rock_ballads", MetadataType.JSON_RADIOS))
        stations.add(RadioStation(61, "Radio S 2000-te Folk", R.drawable.logo_s_2000te_folk, "http://edge-de-04.maksnet.tv/asmedia/s_2000-te_folk/playlist.m3u8", "https://www.radios.rs/includes/get/now-playing-json.php?radio=s_2000-te_folk", MetadataType.JSON_RADIOS))
        stations.add(RadioStation(62, "Radio S Lounge", R.drawable.logo_s_lounge, "http://edge-rs-05.maksnet.tv/asmedia/s_lounge2/playlist.m3u8", "https://www.radios.rs/includes/get/now-playing-json.php?radio=s_lounge2", MetadataType.JSON_RADIOS))
        stations.add(RadioStation(63, "Radio S Easy", R.drawable.logo_s_easy, "http://edge-de-04.maksnet.tv/asmedia/radio_s_easy/playlist.m3u8", "https://www.radios.rs/includes/get/now-playing-json.php?radio=s_easy", MetadataType.JSON_RADIOS))
        stations.add(RadioStation(64, "Radio S Rock", R.drawable.logo_s_rock, "http://edge-de-04.maksnet.tv/asmedia/radio_s_rock/playlist.m3u8", "https://www.radios.rs/includes/get/now-playing-json.php?radio=s_rock", MetadataType.JSON_RADIOS))
        stations.add(RadioStation(65, "Radio S Narodni", R.drawable.logo_s_narodni, "http://edge-rs-04.maksnet.tv/asmedia/radio_s_kafana/playlist.m3u8", "https://www.radios.rs/includes/get/now-playing-json.php?radio=s_folk", MetadataType.JSON_RADIOS))
        stations.add(RadioStation(66, "Radio S Mix", R.drawable.logo_s_mix, "http://edge-de-04.maksnet.tv/asmedia/radio_s_mix/playlist.m3u8", "https://www.radios.rs/includes/get/now-playing-json.php?radio=s_mix", MetadataType.JSON_RADIOS))
        stations.add(RadioStation(67, "Radio S Jazz", R.drawable.logo_s_jazz, "http://edge-rs-01.maksnet.tv/asmedia/s_jazz/playlist.m3u8", "https://www.radios.rs/includes/get/now-playing-json.php?radio=s_jazz", MetadataType.JSON_RADIOS))
        stations.add(RadioStation(68, "Radio S Sport", R.drawable.logo_s_sport, "http://edge-de-04.maksnet.tv/asmedia/s_sport/playlist.m3u8", "https://www.radios.rs/includes/get/now-playing-json.php?radio=s_sport", MetadataType.JSON_RADIOS))
        stations.add(RadioStation(69, "Radio S Zavicaj", R.drawable.logo_s_zavicaj, "http://edge-rs-04.maksnet.tv/asmedia/radio_s_zavicaj/playlist.m3u8", "https://www.radios.rs/includes/get/now-playing-json.php?radio=s_zavicaj", MetadataType.JSON_RADIOS))

// 4. HIT FM
        stations.add(RadioStation(70, "Hit FM", R.drawable.logo_hit_radio, "https://streaming.tdiradio.com/hitbezreklama.mp3", "", MetadataType.AUTO))
        stations.add(RadioStation(71, "Hit FM Modern", R.drawable.logo_hitfm_modern, "https://samira.dontstopmusic.cyou/lovely12", "", MetadataType.AUTO))
        stations.add(RadioStation(72, "Hit FM Party", R.drawable.logo_hitfm_party, "https://aisha.dontstopmusic.cyou/lovely5", "", MetadataType.AUTO))
        stations.add(RadioStation(73, "Hit FM Romansa", R.drawable.logo_hitfm_romansa, "https://aisha.dontstopmusic.cyou/lovely4", "", MetadataType.AUTO))
        stations.add(RadioStation(74, "Hit FM Dance", R.drawable.logo_hitfm_dance, "https://aisha.dontstopmusic.cyou/lovely6", "", MetadataType.AUTO))
        stations.add(RadioStation(75, "Hit FM Puls 90", R.drawable.logo_hitfm_puls90, "https://aisha.dontstopmusic.cyou/lovely7", "", MetadataType.AUTO))
        stations.add(RadioStation(76, "Hit FM Vintage", R.drawable.logo_hitfm_vintage, "https://aisha.dontstopmusic.cyou/lovely8", "", MetadataType.AUTO))
        stations.add(RadioStation(77, "Hit FM Forever Hits", R.drawable.logo_hitfm_foreverhits, "https://aisha.dontstopmusic.cyou/lovely9", "", MetadataType.AUTO))
        stations.add(RadioStation(78, "Hit FM Gold", R.drawable.logo_hitfm_gold, "https://aisha.dontstopmusic.cyou/lovely10", "", MetadataType.AUTO))
        stations.add(RadioStation(79, "Hit FM Ambient", R.drawable.logo_hitfm_ambient, "https://aisha.dontstopmusic.cyou/lovely2", "", MetadataType.AUTO))
        stations.add(RadioStation(80, "Hit FM ExYuHit", R.drawable.logo_hitfm_exyuhit, "https://aisha.dontstopmusic.cyou/lovely11", "", MetadataType.AUTO))
        stations.add(RadioStation(81, "TDI Crna Gora", R.drawable.logo_tdi, "https://streaming.tdiradio.com/crnagora.mp3", "", MetadataType.AUTO))

// 5. RSG
        stations.add(RadioStation(82, "RSG Radio Relax", R.drawable.logo_rsg_relax, "https://stream.rsg.rs/relax/electronic.mp3", "", MetadataType.STANDARD))
        stations.add(RadioStation(83, "RSG Radio ExYu", R.drawable.logo_rsg_exyu, "https://stream.rsg.rs/exyu/electronic.mp3", "", MetadataType.STANDARD))
        stations.add(RadioStation(84, "RSG Radio Pop", R.drawable.logo_rsg_pop, "https://stream.rsg.rs/pop/electronic.mp3", "", MetadataType.STANDARD))
        stations.add(RadioStation(85, "RSG Radio Dvojka", R.drawable.logo_rsg_dvojka, "https://stream.rsg.rs/dvojka/electronic.mp3", "", MetadataType.STANDARD))
        stations.add(RadioStation(86, "RSG Radio Folk", R.drawable.logo_rsg_folk, "https://stream.rsg.rs/folk/electronic.mp3", "", MetadataType.STANDARD))
        stations.add(RadioStation(87, "RSG Radio Cafe", R.drawable.logo_rsg_cafe, "https://stream.rsg.rs/partytime/electronic.mp3", "", MetadataType.STANDARD))
        stations.add(RadioStation(88, "RSG Radio Trending", R.drawable.logo_rsg_trending, "https://stream.rsg.rs/trending/electronic.mp3", "", MetadataType.STANDARD))
        stations.add(RadioStation(89, "RSG Stari Grad", R.drawable.logo_rsg_starigrad, "https://stream.rsg.rs/live/electronic.mp3", "", MetadataType.STANDARD))

// 6. OSTALI
        stations.add(RadioStation(90, "Play Radio", R.drawable.logo_play_play, "https://stream.playradio.rs:8443/play.mp3", "", MetadataType.STANDARD))
        stations.add(RadioStation(91, "Play Rock", R.drawable.logo_play_rock, "https://stream.playradio.rs:8443/rock.mp3", "", MetadataType.STANDARD))
        stations.add(RadioStation(92, "Play Party", R.drawable.logo_play_party, "https://stream.playradio.rs:8443/party.mp3", "", MetadataType.STANDARD))
        stations.add(RadioStation(93, "Play Urban", R.drawable.logo_play_urban, "https://stream.playradio.rs:8443/urban.mp3", "", MetadataType.STANDARD))
        stations.add(RadioStation(94, "Play Soft", R.drawable.logo_play_soft, "https://stream.playradio.rs:8443/soft.mp3", "", MetadataType.STANDARD))
        stations.add(RadioStation(95, "Play Balkan", R.drawable.logo_play_balkan, "https://stream.playradio.rs:8443/balkan.mp3", "", MetadataType.STANDARD))
        stations.add(RadioStation(96, "Karolina", R.drawable.logo_karolina, "https://streaming.tdiradio.com/karolina.mp3", "", MetadataType.STANDARD))
        stations.add(RadioStation(97, "Radio TRI", R.drawable.logo_radio_tri, "http://radiotri-128.streaming.rs:9300/;stream.nsv", "https://www.radiotri.rs/live/nowonair.php", MetadataType.SHOUTCAST_TEXT))


        return stations
    }

    fun getStationById(id: Int): RadioStation? {
        return getStations().find { it.id == id }
    }

    fun getPopularDefaultStations(): List<RadioStation> {
        val all = getStations()
        val popularIds = listOf(1, 2, 4, 10, 33, 70) // TDI, Balkan Dance, Naxi ExYu Rock, Naxi House, Radio S1, Hit FM
        return popularIds.mapNotNull { id -> all.find { it.id == id } }
    }
}
