package com.hafiyanjye.app.onboarding

/**
 * Real engine answers (local engine, demo job seeker demo.provider1, 2026-11-18),
 * trimmed to what setup reads. Keep them as the engine sends them.
 */
object OnboardingFixtures {

    const val PROFILE = """{"ok":true,"data":{"id":1,"userId":2,"displayName":"Jean Bosco Habimana",
        "picture":null,
        "bio":"Certified electrician (RURA/REG-registered) with 12 years of residential and commercial work.",
        "yearsExperience":12,"priceMinRwf":10000,"priceUnit":"job","priceMaxRwf":60000,"lat":-1.9473,"lng":30.1252,
        "serviceRadiusKm":10,"addressLabel":"Kimironko, Kigali","photos":[],"languages":["rw","en","fr"],
        "completenessScore":70,"profileStage":"ready",
        "completenessTips":["Add a profile photo so clients see who is coming","Add photos of your work"],
        "availability":"offline","maxConcurrentJobs":5,"ratingAvg":4.86,"ratingCount":7,"completedJobs":140,
        "categories":[{"id":1,"slug":"electrician","nameEn":"Electrician","nameRw":"Umutekinisiye w'amashanyarazi",
        "nameFr":"Électricien","icon":"Zap","listed":true}],"level":"gold","replyMedianSec":32},
        "meta":{"requestId":"a456d285-b9e8-4116-8fed-1e781c1d47d9","timestamp":"2026-11-17T23:31:24.942Z"}}"""

    const val NO_PROFILE = """{"ok":true,"data":null,"meta":{"requestId":"r","timestamp":"2026-11-17T23:31:24.942Z"}}"""

    const val DOCUMENTS = """{"ok":true,"data":[{"id":2,"kind":"cv","title":"CV","issuer":null,"issuedYear":null,
        "expiresOn":null,"expired":false,"categorySlugs":[],"status":"checked","checkNote":null,
        "cvSummary":"12 years of residential and commercial wiring; IPRC electrical diploma",
        "fileUrl":"/api/documents/2/file","createdAt":"2026-11-13T20:32:30.096Z"},
        {"id":1,"kind":"certificate","title":"Electrical installation certificate (Level 5)","issuer":"IPRC Kigali",
        "issuedYear":2013,"expiresOn":null,"expired":false,"categorySlugs":["electrician"],"status":"checked",
        "checkNote":null,"cvSummary":null,"fileUrl":"/api/documents/1/file","createdAt":"2026-11-13T20:32:30.086Z"}],
        "meta":{"requestId":"40e5615d-b600-4828-a89b-20b87c5767d8","timestamp":"2026-11-17T23:31:24.956Z"}}"""

    /** A document the job seeker filled in by hand, with an expiry date (shape from core/providers/documents.ts). */
    const val SELF_DECLARED = """{"ok":true,"data":{"id":7,"kind":"certificate","title":"First aid",
        "issuer":"Red Cross","issuedYear":2024,"expiresOn":"2027-06-30T23:59:59.000Z","expired":false,
        "categorySlugs":["electrician"],"status":"self_declared","checkNote":null,"cvSummary":null,
        "fileUrl":"/api/documents/7/file","createdAt":"2026-11-18T08:00:00.000Z"},
        "meta":{"requestId":"r","timestamp":"2026-11-18T08:00:00.000Z"}}"""

    const val CATEGORIES = """{"ok":true,"data":[
        {"id":1,"slug":"electrician","nameEn":"Electrician","nameRw":"Umutekinisiye w'amashanyarazi",
        "nameFr":"Électricien","icon":"Zap","listed":true},
        {"id":2,"slug":"plumber","nameEn":"Plumber","nameRw":"Umufundi w'amazi","nameFr":"Plombier",
        "icon":"Droplets","listed":true},
        {"id":8,"slug":"tailor","nameEn":"Tailor","nameRw":"Umudozi","nameFr":"Tailleur / Couturière",
        "icon":"Scissors","listed":true},
        {"id":40,"slug":"umuzamu","nameEn":"Security guard","nameRw":"Umuzamu","nameFr":"Gardien",
        "icon":"Shield","listed":false}],
        "meta":{"requestId":"r","timestamp":"2026-11-17T23:31:24.956Z"}}"""

    /** POST /api/media answers 201 with a UUID id (not a number). */
    const val MEDIA = """{"ok":true,"data":{"id":"5b0f3d1e-8f0a-4c43-9a55-0c8b1d6e2f10",
        "url":"/api/media/5b0f3d1e-8f0a-4c43-9a55-0c8b1d6e2f10"},"meta":{"requestId":"r","timestamp":"t"}}"""

    const val REGISTERED_EXISTING = """{"ok":true,"data":{"kind":"existing","category":{"id":2,"slug":"plumber",
        "nameEn":"Plumber","nameRw":"Umufundi w'amazi","nameFr":"Plombier","icon":"Droplets","listed":true}},
        "meta":{"requestId":"r","timestamp":"t"}}"""

    const val REMOVED = """{"ok":true,"data":{"deleted":true},"meta":{"requestId":"r","timestamp":"t"}}"""
}
