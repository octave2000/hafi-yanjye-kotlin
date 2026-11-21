package com.hafiyanjye.app.pings

/** Trimmed real responses from the local engine (demo.provider1 and demo.provider41), 2026-11-17. */
object PingsFixtures {

    fun envelope(data: String) = """{"ok":true,"data":$data,"meta":{}}"""

    /** Active scope: a ping this job seeker was chosen for (not hired yet). */
    val chosenPing = """
        {"id":4729,"jobId":4488,"status":"available","wave":1,"distanceKm":0.2,
         "summary":"Kitchen sockets spark when the kettle is plugged in and the breaker trips.",
         "categoryName":"Electrician",
         "requirements":[{"label":"Fix the sparking kitchen sockets","importance":"must"},
                         {"label":"Come today","importance":"nice"}],
         "urgency":"today","budgetRwf":20000,"areaLabel":"near the market","quoteRwf":24000,
         "note":"Price includes transport.","sentAt":"2026-11-13T20:32:31.145Z",
         "expiresAt":"2026-11-13T20:37:31.145Z","direct":false,
         "match":{"sessionId":4487,"seekerName":"Aimable Nshuti","seekerPhone":"+250788000001",
                  "addressLabel":"Kimironko, near the market","lat":-1.9478,"lng":30.1236,
                  "state":"matched","hired":false}}
    """.trimIndent()

    /** History scope: answered "not now". */
    val declinedPing = """
        {"id":3852,"jobId":3633,"status":"not_now","wave":1,"distanceKm":2.5,
         "summary":"Cash-power meter beeps and the power cuts at night","categoryName":"Electrician",
         "requirements":[{"label":"Cash-power meter beeps and the power cuts at night","importance":"must"}],
         "urgency":"now","budgetRwf":20500,"areaLabel":"Kigali","quoteRwf":null,"note":null,
         "sentAt":"2026-11-11T20:32:19.710Z","expiresAt":"2026-11-11T20:37:19.710Z",
         "direct":false,"match":null}
    """.trimIndent()

    val profile = """
        {"id":1,"userId":2,"displayName":"Jean Bosco Habimana","picture":null,
         "bio":"Certified electrician (RURA/REG-registered) with 12 years of","yearsExperience":12,
         "priceMinRwf":10000,"priceUnit":"job","priceMaxRwf":60000,"lat":-1.9473,"lng":30.1252,
         "serviceRadiusKm":10,"addressLabel":"Kimironko, Kigali","photos":[],"languages":["rw","en","fr"],
         "completenessScore":70,"profileStage":"ready",
         "completenessTips":["Add a profile photo so clients see who is coming","Add photos of your work"],
         "availability":"offline","maxConcurrentJobs":5,"ratingAvg":4.86,"ratingCount":7,"completedJobs":140,
         "categories":[{"id":1,"slug":"electrician","nameEn":"Electrician",
                        "nameRw":"Umutekinisiye w'amashanyarazi","nameFr":"Électricien","icon":"Zap","listed":true}],
         "level":"gold","replyMedianSec":32}
    """.trimIndent()

    /** A new job seeker who was paused after missed pings. */
    val newcomerStats = """
        {"level":"new","nextLevel":"silver",
         "nextLevelChecklist":[{"label":"Get through your first 10 job requests","met":false,"progress":"8 of 10"},
                               {"label":"Keep a 4.2★ rating or better","met":true,"progress":"5.0★ of 4.2★"}],
         "replyMedianSec":117,"answerRate":0.625,"pingsCounted":8,"fastStreak":0,"bestStreak":2,
         "missedStreak":3,"missedWhileOffline":7,"pausedAt":"2026-11-14T13:32:36.447Z",
         "week":{"pingsReceived":0,"answered":0,"chosen":0,"jobsDone":0,"quotedEarningsRwf":0}}
    """.trimIndent()

    val newcomerRanking = """
        {"standingTotal":28.1,
         "parts":[{"key":"fit","label":"Job fit","points":null,"max":50,
                   "detail":"Depends on each job.","items":[]},
                  {"key":"trust","label":"Trust","points":16.2,"max":30,"detail":"Earned from your reviews.",
                   "items":[{"label":"Rating","points":9,"max":12},{"label":"Reliability","points":6.4,"max":8}]}],
         "nextSteps":[{"label":"Upload your plumber certificate","gain":2}],
         "profileStage":"ready",
         "starter":{"boostPoints":4.5,"jobsLeft":9,"seatEligible":true,
                    "steps":[{"label":"Make your profile Ready (60+)","unlocks":"Starter seats","done":true},
                             {"label":"Finish 10 jobs","unlocks":"You graduate","done":false}]}}
    """.trimIndent()

    val review = """
        {"id":930,"rating":5,"text":"Arrived on time, explained the problem clearly and fixed it in under an hour.",
         "reviewerName":"Claudine Mukarugwiza","createdAt":"2026-11-10T23:32:19.585Z"}
    """.trimIndent()
}
