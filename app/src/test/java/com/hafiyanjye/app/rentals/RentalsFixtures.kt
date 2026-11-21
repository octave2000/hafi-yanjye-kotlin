package com.hafiyanjye.app.rentals

/** Trimmed real engine responses (demo data, localhost:3100), wrapped as the engine sends them. */
object RentalsFixtures {
    private fun ok(data: String) = """{"ok":true,"data":$data,"meta":{"requestId":"test"}}"""

    /** GET /api/v1/listings/search?kind=car, first two items. */
    val carSearch = ok(
        """
        {"items":[
          {"id":84,"kind":"car","title":"Mercedes C-Class with chauffeur","priceRwf":85000,"pricePeriod":"day",
           "photo":"/api/media/dc1309ee-2096-4a13-9972-d94664ed6f8a","photoCount":2,"lat":-1.953,"lng":30.061,
           "areaLabel":"KN 67 St, Kiyovu","distanceKm":1,
           "attributes":{"car":{"body":"sedan","fuel":"petrol","make":"Mercedes-Benz","year":2018,"model":"C-Class",
             "seats":5,"withDriver":true,"transmission":"automatic"},"home":null}},
          {"id":82,"kind":"car","title":"Toyota Hilux double cabin","priceRwf":60000,"pricePeriod":"day",
           "photo":"/api/media/eb86b544-51b2-448b-9645-92ebf9a266da","photoCount":1,"lat":-1.922,"lng":30.066,
           "areaLabel":"KG 14 Ave, Gisozi","distanceKm":2.5,
           "attributes":{"car":{"body":"pickup","fuel":"diesel","make":"Toyota","year":2016,"model":"Hilux",
             "seats":5,"withDriver":false,"transmission":"manual"},"home":null}}
        ],"center":{"lat":-1.9441,"lng":30.0619},"total":12,"hasMore":false}
        """,
    )

    /** GET /api/v1/listings/98?lat=-1.95&lng=30.06, signed in as the demo job provider. */
    val homeDetail = ok(
        """
        {"id":98,"kind":"home","title":"Furnished apartment in Kiyovu, walk to town","priceRwf":800000,
         "pricePeriod":"month","photo":"/api/media/3f115a87-fe13-4d9c-9a4a-79ebf61f6880","photoCount":3,
         "lat":-1.955,"lng":30.063,"areaLabel":"KN 57 St, Kiyovu","distanceKm":0.6,
         "attributes":{"car":null,"home":{"bedrooms":2,"homeType":"apartment","bathrooms":2,"furnished":true}},
         "description":"Elegant two-bedroom apartment in leafy Kiyovu, a short walk from the city centre.",
         "photos":["/api/media/3f115a87-fe13-4d9c-9a4a-79ebf61f6880","/api/media/8ce4bd3b-c8c5-4bdb-824e-1d4fade8bdf8",
           "/api/media/c250a7f6-5d04-4235-9d1c-730b5288a538"],
         "status":"active",
         "owner":{"userId":430,"name":"Jeanne Mukamana","picture":null,"phone":"+250788000104",
           "memberSince":"2026-11-14T13:09:34.929Z","activeListings":5,"online":false},
         "mine":false,"exact":null,"createdAt":"2026-11-15T16:43:40.690Z","updatedAt":"2026-11-15T16:43:40.690Z"}
        """,
    )

    /** POST /api/v1/listings/ask {"text":"2 bedroom furnished near Kimironko under 400k"}. */
    val askRental = ok(
        """
        {"kind":"rental","filters":{"kind":"home","maxPriceRwf":400000,"minBedrooms":2,"furnished":true},
         "place":"Kimironko",
         "summary":"Homes to rent · 2+ bedrooms · Furnished · Under 400,000 RWF a month · Near Kimironko"}
        """,
    )

    /** POST /api/v1/listings/ask {"text":"need a plumber"}. */
    val askService = ok("""{"kind":"service"}""")

    /** GET /api/v1/listings/99999. */
    const val notFound =
        """{"ok":false,"error":{"code":"NOT_FOUND","message":"This listing isn't available any more"},"meta":{}}"""
}
