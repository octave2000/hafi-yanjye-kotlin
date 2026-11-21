package com.hafiyanjye.app.browse

/** Real answers from the demo engine (Nov 2026), trimmed to what the tests read. */
object BrowseFixtures {
    /** GET /providers/search?lat=-1.95&lng=30.06&limit=2&category=electrician */
    val Search = """
        {"items":[{"id":384,"displayName":"Noelle Isingizwe",
        "picture":"/api/media/292afb34-f133-4d27-b698-506a6947a9f7",
        "bioSnippet":"I offer electrical, masonry and carpentry services here in Kigali.",
        "addressLabel":"Norrsken House Kigali",
        "categories":[{"id":1,"slug":"electrician","nameEn":"Electrician","nameRw":"Umutekinisiye w'amashanyarazi",
        "nameFr":"Électricien","icon":"Zap","listed":true},{"id":4,"slug":"mason","nameEn":"Mason",
        "nameRw":"Umufundi wubaka","nameFr":"Maçon","icon":"BrickWall","listed":true}],"ratingAvg":4.75,
        "ratingCount":4,"completedJobs":5,"yearsExperience":5,"priceMinRwf":10000,"priceUnit":"job",
        "priceMaxRwf":400000,"level":"new","availability":"online","replyMedianSec":95,"languages":["rw",
        "en","fr","sw"],
        "distanceKm":0.5,"coversYou":true,"hasCheckedCredential":false},
        {"id":52,"displayName":"Solange Murekatete","picture":null,
        "bioSnippet":"Electrician for offices and apartments in Kacyiru and Kimihurura.",
        "addressLabel":"Kacyiru, Kigali",
        "categories":[{"id":1,"slug":"electrician","nameEn":"Electrician","nameRw":"Umutekinisiye w'amashanyarazi",
        "nameFr":"Électricien","icon":"Zap","listed":true}],"ratingAvg":5,"ratingCount":3,"completedJobs":47,
        "yearsExperience":7,"priceMinRwf":10000,"priceUnit":"job","priceMaxRwf":50000,"level":"silver",
        "availability":"online","replyMedianSec":221,"languages":["rw","fr"],"distanceKm":3,"coversYou":true,
        "hasCheckedCredential":false}],
        "total":13,"sort":"distance","located":true,"category":{"id":1,"slug":"electrician","nameEn":"Electrician",
        "nameRw":"Umutekinisiye w'amashanyarazi","nameFr":"Électricien","icon":"Zap","listed":true},
        "categoryCounts":{"mason":3,"electrician":13,"plumber":9},"allCount":43}
    """.trimIndent()

    /** GET /providers/1 */
    val Profile = """
        {"id":1,"displayName":"Jean Bosco Habimana","picture":null,
        "bio":"Certified electrician (RURA/REG-registered) with 12 years of residential and commercial work.",
        "yearsExperience":12,"priceMinRwf":10000,"priceUnit":"job","priceMaxRwf":60000,
        "addressLabel":"Kimironko, Kigali","photos":[],"languages":["rw","en","fr"],"ratingAvg":4.86,
        "ratingCount":7,"completedJobs":140,"categories":[{"id":1,"slug":"electrician","nameEn":"Electrician",
        "nameRw":"Umutekinisiye w'amashanyarazi","nameFr":"Électricien","icon":"Zap","listed":true}],
        "level":"gold","replyMedianSec":32,"credentials":[{"id":2,"kind":"cv","title":"CV","issuer":null,
        "issuedYear":null,"status":"checked","expired":false,"categorySlugs":[],
        "cvSummary":"12 years of residential and commercial wiring; IPRC electrical diploma"},
        {"id":1,"kind":"certificate","title":"Electrical installation certificate (Level 5)","issuer":"IPRC Kigali",
        "issuedYear":2013,"status":"checked","expired":false,"categorySlugs":["electrician"],"cvSummary":null}]}
    """.trimIndent()

    /** GET /providers/52/reviews, the second with its reviewer name removed as the engine can send it. */
    val Reviews = """
        [{"id":1040,"rating":5,"text":"Great communication on WhatsApp, sent photos when the job was done.",
        "reviewerName":"Patrick Rukundo","createdAt":"2026-11-03T20:32:26.754Z"},
        {"id":1041,"rating":5,"text":"Best service I've had in Kigali. Fair price and no stress.",
        "reviewerName":null,"createdAt":"2026-10-23T20:32:26.770Z"}]
    """.trimIndent()
}
