package com.hafiyanjye.app.find

/** Real answers from the local engine (2026-11-18), trimmed; meta ids kept short. */
internal object FindFixtures {
    private const val Meta = """"meta":{"requestId":"r1","timestamp":"2026-11-17T23:41:41.066Z"}"""

    const val Created = """{"ok":true,"data":{"kind":"created","jobId":4539,"sessionId":4536},$Meta}"""

    const val Rental = """{"ok":true,"data":{"kind":"rental"},$Meta}"""

    const val PickCategory = """{"ok":true,"data":{"kind":"pick_category","suggestions":[""" +
        """{"id":248,"slug":"dj","nameEn":"DJ","nameRw":"DJ w'ibirori","nameFr":"DJ pour mariages",""" +
        """"icon":"Music","listed":false},""" +
        """{"id":16,"slug":"photographer","nameEn":"Photographer","nameRw":"Umufotozi","nameFr":"Photographe",""" +
        """"icon":"Camera","listed":true}],""" +
        """"summary":"Hot air balloon pilot needed for a wedding","uncovered":false},$Meta}"""

    const val Uncovered = """{"ok":true,"data":{"kind":"pick_category","suggestions":[],""" +
        """"summary":"General help needed around the home","uncovered":true},$Meta}"""

    const val BudgetRefused = """{"ok":false,"error":{"code":"BAD_REQUEST",""" +
        """"message":"Budget must be between 1,000 and 10,000,000 RWF"},$Meta}"""

    const val AskRentalHome = """{"ok":true,"data":{"kind":"rental","filters":{"kind":"home",""" +
        """"maxPriceRwf":400000,"minBedrooms":2,"furnished":true,"homeType":"apartment"},"place":"Kimironko",""" +
        """"summary":"Homes to rent · 2+ bedrooms · Apartment · Furnished · Under 400,000 RWF a month"},$Meta}"""

    const val AskService = """{"ok":true,"data":{"kind":"service"},$Meta}"""

    const val NearbyPlumber = """{"ok":true,"data":{"online":2,"radiusKm":10,"category":{"id":2,"slug":"plumber",""" +
        """"nameEn":"Plumber","nameRw":"Umufundi w'amazi","nameFr":"Plombier","icon":"Droplets","listed":true},""" +
        """"typicalReplySeconds":178},$Meta}"""

    const val Categories = """{"ok":true,"data":[""" +
        """{"id":1,"slug":"electrician","nameEn":"Electrician","nameRw":"Umutekinisiye w'amashanyarazi",""" +
        """"nameFr":"Électricien","icon":"Zap","listed":true},""" +
        """{"id":246,"slug":"security_guard","nameEn":"Security guard","nameRw":"Umuzamu",""" +
        """"nameFr":"Agent de sécurité",""" +
        """"icon":"Shield","listed":false}],$Meta}"""
}
