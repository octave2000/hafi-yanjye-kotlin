package com.hafiyanjye.app.talent

/** Trimmed real answers from the local engine (demo job seeker demo.provider1, 2026-11-18). */
object TalentFixtures {

    /** The `data` of GET /api/v1/talent/me. */
    private val profileData = """
        {"fullName":"Jean Bosco Habimana","headline":"Electrician · 6 years on homes and shops",
        "summary":"","email":"demo.provider1@hafiyanjye.test","phone":"+250788123401","location":"Kicukiro, Kigali",
        "photo":null,"skills":["House wiring","Solar installation","Fault finding"],
        "languages":[{"name":"Kinyarwanda","level":"native"},{"name":"English","level":"good"}],
        "experience":[{"end":null,"role":"Electrician","start":"2021-03","company":"Volta Works",
        "details":"Wired 40 new homes in Kicukiro\nTrained 2 apprentices"}],
        "education":[{"end":"2018","start":"2016","degree":"A1 Electrical Technology","school":"IPRC Kigali"}],
        "links":[{"url":"https://linkedin.com/in/demo","label":""}],"desiredRoles":["Site electrician"],
        "openToWork":true,"id":3,"style":{"template":"bold","accent":"#0b7a40","font":"poppins","showPhoto":true},
        "isPublic":true,"cvFile":null,"completeness":75,"missing":["summary","photo"],
        "updatedAt":"2026-11-17T17:37:47.813Z"}
    """.trimIndent()

    private fun envelope(data: String) =
        """{"ok":true,"data":$data,"meta":{"requestId":"fb85778c","timestamp":"2026-11-17T23:31:56.763Z"}}"""

    /** GET /api/v1/talent/me */
    val profile = envelope(profileData)

    /**
     * POST /api/talent/cv, shaped as cv-routes.ts answers (TalentCvUploadDto). Built from the
     * real profile rather than recorded: uploading would overwrite a demo account's CV file.
     */
    val cvUpload = envelope("""{"profile":$profileData,"parsed":true,"filled":["summary"]}""")

    /** GET /api/v1/talent/me before the job seeker has a profile (demo.provider40). */
    val noProfile = """
        {"ok":true,"data":null,"meta":{"requestId":"3a03336a-06c6-472f-a8d4-0668b1a0bb2d",
        "timestamp":"2026-11-17T23:32:03.455Z"}}
    """.trimIndent()

    /** POST /api/v1/talent/me/polish */
    val polish = """
        {"ok":true,"data":{"headline":"Electrician for Homes and Shops",
        "summary":"Electrician with six years of experience on homes and shops."},
        "meta":{"requestId":"87920600","timestamp":"2026-11-17T23:32:02.894Z"}}
    """.trimIndent()

    /** POST /api/v1/talent/me/interview, first round with follow-ups. */
    val interview = """
        {"ok":true,"data":{"draft":{"headline":"Electrician","summary":"Electrician with six years of experience.",
        "location":"Kicukiro, Kigali","skills":["House wiring","Solar installation"],
        "languages":[{"name":"Kinyarwanda","level":"native"}],
        "experience":[{"role":"Electrician","company":"Volta Works","start":"2021-03","end":null,
        "details":"Wired 40 new homes in Kicukiro"}],
        "education":[{"school":"IPRC Kigali","degree":"A1 Electrical Technology","start":"2016","end":"2018"}],
        "desiredRoles":["Site electrician"]},
        "followUps":["Could you describe the shops you have wired and the work you did there?",
        "Do you have any certificates for solar installation?",
        "What tools or equipment do you use most in your daily work?"],"ai":true},
        "meta":{"requestId":"cf6e81a0","timestamp":"2026-11-17T23:32:10.691Z"}}
    """.trimIndent()

    /** POST /api/v1/talent/me/interview with no answers. */
    val interviewNoAnswers = """
        {"ok":false,"error":{"code":"BAD_REQUEST","message":"Answer at least one question"},
        "meta":{"requestId":"831255a7","timestamp":"2026-11-17T23:32:10.707Z"}}
    """.trimIndent()
}
