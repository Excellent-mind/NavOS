package com.excellentmindblow.navos

/**
 * Place kinds ("Shopping mall") and station facilities ("Toilet") are kept in
 * English inside the app - they are saved in history and caches and compared
 * in code - and only put into the app's language when shown, here.
 */
object Labels {
    fun show(label: String?): String? = label?.let { if (Lang.malay) MS[it] ?: it else it }

    /** EV charger details ("Plugs: CCS2 (50 kW)\nChargers: 2"). */
    fun spec(text: String?): String? =
        if (text == null || !Lang.malay) text else text.replace("Plugs:", "Plag:").replace("Chargers:", "Pengecas:").replace("Spaces:", "Petak:")

    private val MS =
        mapOf(
            // Place kinds (search/PlaceCategory)
            "Shopping mall" to "Pusat beli-belah",
            "Supermarket" to "Pasar raya",
            "Shop" to "Kedai",
            "Petrol" to "Stesen minyak",
            "Restaurant" to "Restoran",
            "Food court" to "Medan selera",
            "Cafe" to "Kafe",
            "Hospital" to "Hospital",
            "Clinic" to "Klinik",
            "Pharmacy" to "Farmasi",
            "School" to "Sekolah",
            "University" to "Universiti",
            "College" to "Kolej",
            "Hotel" to "Hotel",
            "Parking" to "Parking",
            "Place of worship" to "Rumah ibadat",
            "Bank" to "Bank",
            "Police" to "Balai polis",
            "Toll plaza" to "Plaza tol",
            "Airport" to "Lapangan terbang",
            "Airport terminal" to "Terminal lapangan terbang",
            "Park" to "Taman",
            "Attraction" to "Tempat menarik",
            "Museum" to "Muzium",
            "Stadium" to "Stadium",
            "Train station" to "Stesen kereta api",
            "Bus stop" to "Perhentian bas",
            "Road" to "Jalan",
            "Area" to "Kawasan",
            "Address" to "Alamat",
            "Building" to "Bangunan",
            // Station facilities (poi/PoiService)
            "Toilet" to "Tandas",
            "Surau" to "Surau",
            "ATM" to "ATM",
            "EV charging" to "Cas EV",
            "Car wash" to "Cuci kereta",
            "Air pump" to "Pam angin",
            "24 hours" to "24 jam",
            "Free to use" to "Percuma",
            // Parking (poi/PoiService.fetchParking)
            "Free" to "Percuma",
            "Paid" to "Berbayar",
            "Covered" to "Berbumbung",
            "Disabled parking" to "Parking OKU",
        )
}
