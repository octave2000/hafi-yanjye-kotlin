package com.hafiyanjye.app.listings

/** Real engine answers (local engine, 2026-11-18, demo job seeker), trimmed only of `meta`. */
object ListingsFixtures {
    /** Test listing 99 after it was created, edited and paused (GET /listings/99 as its owner). */
    const val LISTING = """{"id":99,"kind":"car","title":"Toyota RAV4 2019, self drive",
"priceRwf":48000,"pricePeriod":"day","photo":"/api/media/c2a3019d-602a-4af6-9273-c18f1af58018","photoCount":1,
"lat":-1.954,"lng":30.093,"areaLabel":"Remera, Kigali","distanceKm":0,
"attributes":{"car":{"body":"suv","fuel":"petrol","make":"Toyota","year":2019,"model":"RAV4","seats":5,
"withDriver":false,"transmission":"automatic"},"home":null},
"description":"Test listing from the Android review. Clean, serviced.",
"photos":["/api/media/c2a3019d-602a-4af6-9273-c18f1af58018"],"status":"paused",
"owner":{"userId":2,"name":"Jean Bosco Habimana","picture":null,"phone":"+250788123401",
"memberSince":"2026-11-12T16:24:15.054Z","activeListings":0,"online":true},"mine":true,
"exact":{"lat":-1.9536,"lng":30.0927,"addressLabel":"Remera, Kigali"},
"createdAt":"2026-11-17T23:50:45.013Z","updatedAt":"2026-11-17T23:50:51.915Z"}"""

    /** GET /listings/mine. */
    val MINE = """{"ok":true,"data":[$LISTING],"meta":{}}"""

    /** GET /listings/99 and POST /listings/save. */
    val ONE = """{"ok":true,"data":$LISTING,"meta":{}}"""

    /** POST /listings/save with a price under 1,000. */
    const val SAVE_REFUSED = """{"ok":false,"error":{"code":"BAD_REQUEST",
"message":"Price must be between 1,000 and 50,000,000 RWF"},"meta":{}}"""

    /** POST /listings/status. */
    const val STATUS_OK = """{"ok":true,"data":{"ok":true},"meta":{}}"""

    /** POST /api/media?kind=listing. */
    const val UPLOADED = """{"ok":true,"data":{"id":"c2a3019d-602a-4af6-9273-c18f1af58018",
"url":"/api/media/c2a3019d-602a-4af6-9273-c18f1af58018"},"meta":{}}"""

    /** GET /listings/99 by someone else while it is paused. */
    const val NOT_AVAILABLE = """{"ok":false,"error":{"code":"NOT_FOUND",
"message":"This listing isn't available any more"},"meta":{}}"""
}
