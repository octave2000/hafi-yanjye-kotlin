package com.hafiyanjye.app.match

/*
 * Real engine responses (GET /api/v1/jobs/{id} and /api/v1/jobs) from the local demo data,
 * trimmed: long texts cut at 80 characters, extra candidates and backups dropped. Captured 2026-11-18.
 */
internal object Fixtures {
    val MatchedJob = """
        {
          "job": {
            "id": 4523,
            "rawText": "an elecrician",
            "summary": "Electrician needed for electrical work",
            "category": {
              "id": 1,
              "slug": "electrician",
              "nameEn": "Electrician",
              "nameRw": "Umutekinisiye w'amashanyarazi",
              "nameFr": "Électricien",
              "icon": "Zap",
              "listed": true
            },
            "requirements": [],
            "urgency": "flexible",
            "lat": -1.9510594,
            "lng": 30.060001,
            "addressLabel": "My location",
            "budgetRwf": null,
            "status": "matched",
            "notifyMe": false,
            "sessionId": 4522,
            "createdAt": "2026-11-17T12:04:18.648Z"
          },
          "session": {
            "id": 4522,
            "jobId": 4523,
            "state": "matched",
            "stats": {
              "pinged": 10,
              "seen": 0,
              "available": 0,
              "declined": 0,
              "wave": 3,
              "radiusKm": 30,
              "firstReplySeconds": null,
              "typicalReplySeconds": 185
            },
            "replies": [],
            "shortlistDueAt": null,
            "candidates": [
              {
                "hasCv": true,
                "level": "gold",
                "reasons": [
                  "7.3 km away",
                  "4.9★ from 7 reviews",
                  "12 years of experience",
                  "140 jobs done here"
                ],
                "shortId": "c2",
                "concerns": [
                  "Price not shared yet",
                  "Hasn't confirmed availability"
                ],
                "quoteRwf": null,
                "baseScore": 0.696,
                "priceUnit": "job",
                "ratingAvg": 4.86,
                "responded": false,
                "distanceKm": 7.3,
                "modelScore": null,
                "priceMaxRwf": 60000,
                "priceMinRwf": 10000,
                "ratingCount": 7,
                "completedJobs": 140,
                "hasCertificate": true,
                "replyMedianSec": 32,
                "yearsExperience": 12,
                "completenessScore": 70,
                "missingCredential": false
              }
            ],
            "round": null,
            "roundsPlayed": 0,
            "turnedDown": 0,
            "askingMore": null,
            "pick": {
              "providerId": 1,
              "displayName": "Jean Bosco Habimana",
              "picture": null,
              "bio": "Certified electrician (RURA/REG-registered) with 12 years of residential and ...",
              "phone": "+250788123401",
              "distanceKm": 7.3,
              "ratingAvg": 4.86,
              "ratingCount": 7,
              "yearsExperience": 12,
              "completedJobs": 140,
              "quoteRwf": null,
              "priceMinRwf": 10000,
              "priceUnit": "job",
              "priceMaxRwf": 60000,
              "categories": [
                "Electrician"
              ],
              "level": "gold",
              "replyMedianSec": 32,
              "credentials": [
                {
                  "id": 2,
                  "kind": "cv",
                  "title": "CV",
                  "issuer": null,
                  "issuedYear": null,
                  "status": "checked",
                  "expired": false,
                  "categorySlugs": [],
                  "cvSummary": "12 years of residential and commercial wiring; IPRC electrical diploma",
                  "fileUrl": "/api/documents/2/file"
                },
                {
                  "id": 1,
                  "kind": "certificate",
                  "title": "Electrical installation certificate (Level 5)",
                  "issuer": "IPRC Kigali",
                  "issuedYear": 2013,
                  "status": "checked",
                  "expired": false,
                  "categorySlugs": [
                    "electrician"
                  ],
                  "cvSummary": null,
                  "fileUrl": "/api/documents/1/file"
                }
              ]
            },
            "hiredAt": null,
            "direct": null,
            "backups": [
              {
                "providerId": 50,
                "displayName": "Hyacinthe Mukiza",
                "picture": null,
                "bio": "Electrical installations for homes and small shops near Amah",
                "phone": null,
                "distanceKm": 5.8,
                "ratingAvg": 4.67,
                "ratingCount": 6,
                "yearsExperience": 9,
                "completedJobs": 96,
                "quoteRwf": null,
                "priceMinRwf": 12000,
                "priceUnit": "job",
                "priceMaxRwf": 70000,
                "categories": [
                  "Electrician",
                  "AC & fridge repair"
                ],
                "level": "silver",
                "replyMedianSec": 172,
                "credentials": []
              }
            ],
            "alternatives": [],
            "explanation": {
              "headline": "An experienced, well-rated electrician 7.3 km away",
              "whatToSay": "Hello, I need an electrician for electrical work in my home in Kigali. Are yo...",
              "backupsNote": "If this provider does not reply, two other electricians are ready as backups,...",
              "whyThisPerson": "This provider has 12 years of electrical work and a checked Level 5 installat...",
              "questionsToAsk": [
                "What is your total price, including any transport cost to reach me?",
                "When can you come, and can you fix the problem the same day?",
                "Will you bring the materials, and what would they cost?",
                "Can I pay by MTN MoMo or Airtel Money once the work is done?"
              ]
            },
            "canReview": false,
            "reviewed": false,
            "updatedAt": "2026-11-17T12:04:29.934Z"
          }
        }
    """.trimIndent()

    val CompletedDirectJob = """
        {
          "job": {
            "id": 4520,
            "rawText": "Physics revision for S6 national exams this weekend",
            "summary": "Seeker needs physics revision help for S6 national exams this weekend.",
            "category": {
              "id": 10,
              "slug": "tutor",
              "nameEn": "Tutor",
              "nameRw": "Umwarimu wigisha mu rugo",
              "nameFr": "Professeur particulier",
              "icon": "GraduationCap",
              "listed": true
            },
            "requirements": [
              {
                "label": "Physics tutoring for S6 exams",
                "importance": "must"
              },
              {
                "label": "Available this weekend",
                "importance": "must"
              }
            ],
            "urgency": "this_week",
            "lat": -1.9493,
            "lng": 30.1262,
            "addressLabel": "Kimironko, near the market",
            "budgetRwf": null,
            "status": "completed",
            "notifyMe": false,
            "sessionId": 4519,
            "createdAt": "2026-11-17T10:48:27.978Z"
          },
          "session": {
            "id": 4519,
            "jobId": 4520,
            "state": "completed",
            "stats": {
              "pinged": 1,
              "seen": 1,
              "available": 1,
              "declined": 0,
              "wave": 1,
              "radiusKm": 3,
              "firstReplySeconds": 0,
              "typicalReplySeconds": 177
            },
            "replies": [
              {
                "distanceKm": 2.8,
                "quoteRwf": null,
                "secondsAfterStart": 0,
                "level": "silver"
              }
            ],
            "shortlistDueAt": null,
            "candidates": [
              {
                "hasCv": false,
                "level": "silver",
                "reasons": [
                  "2.8 km away",
                  "5.0★ from 2 reviews",
                  "4 years of experience",
                  "42 jobs done here"
                ],
                "shortId": "c1",
                "concerns": [
                  "Price not shared yet"
                ],
                "quoteRwf": null,
                "baseScore": 0.685,
                "ratingAvg": 5,
                "responded": true,
                "distanceKm": 2.8,
                "modelScore": null,
                "priceMaxRwf": 30000,
                "priceMinRwf": 10000,
                "ratingCount": 2,
                "completedJobs": 42,
                "hasCertificate": false,
                "replyMedianSec": 182,
                "yearsExperience": 4,
                "completenessScore": 70,
                "missingCredential": false,
                "priceUnit": "job"
              }
            ],
            "round": null,
            "roundsPlayed": 0,
            "turnedDown": 0,
            "askingMore": null,
            "pick": {
              "providerId": 18,
              "displayName": "Kevin Habiyaremye",
              "picture": null,
              "bio": "Maths, physics and coding tutor (BSc, University of Rwanda).",
              "phone": "+250788123418",
              "distanceKm": 2.8,
              "ratingAvg": 5,
              "ratingCount": 2,
              "yearsExperience": 4,
              "completedJobs": 43,
              "quoteRwf": null,
              "priceMinRwf": 10000,
              "priceUnit": "hour",
              "priceMaxRwf": null,
              "categories": [
                "Tutor",
                "Computer & IT repair"
              ],
              "level": "silver",
              "replyMedianSec": 182,
              "credentials": []
            },
            "hiredAt": "2026-11-17T10:48:33.885Z",
            "direct": {
              "providerId": 18,
              "displayName": "Kevin Habiyaremye",
              "picture": null,
              "answer": "accepted"
            },
            "backups": [],
            "alternatives": [],
            "explanation": {
              "headline": "A physics tutor with S6 exam experience 2.8 km away",
              "whatToSay": "Hello, I need physics revision help for my S6 national exams. Are you availab...",
              "backupsNote": "If this provider does not reply, we will help you find other nearby tutors so...",
              "whyThisPerson": "This provider prepares students for S6 national exams and holds a BSc from th...",
              "questionsToAsk": [
                "Are you free this weekend, and what time could we start?",
                "What is your total price for the sessions, including any transport costs?",
                "Do you accept MTN MoMo or Airtel Money payment?"
              ]
            },
            "canReview": true,
            "reviewed": false,
            "updatedAt": "2026-11-17T10:53:20.703Z"
          }
        }
    """.trimIndent()

    val NobodyFreeJob = """
        {
          "job": {
            "id": 4524,
            "rawText": "a guard",
            "summary": "Security guard service requested, details unclear",
            "category": {
              "id": 22,
              "slug": "other",
              "nameEn": "Other services",
              "nameRw": "Izindi serivisi",
              "nameFr": "Autres services",
              "icon": "Wrench",
              "listed": false
            },
            "requirements": [],
            "urgency": "flexible",
            "lat": -1.9510502,
            "lng": 30.0599902,
            "addressLabel": "My location",
            "budgetRwf": null,
            "status": "no_providers",
            "notifyMe": false,
            "sessionId": 4523,
            "createdAt": "2026-11-17T12:09:24.791Z"
          },
          "session": {
            "id": 4523,
            "jobId": 4524,
            "state": "no_providers",
            "stats": {
              "pinged": 0,
              "seen": 0,
              "available": 0,
              "declined": 0,
              "wave": 3,
              "radiusKm": 30,
              "firstReplySeconds": null,
              "typicalReplySeconds": null
            },
            "replies": [],
            "shortlistDueAt": null,
            "candidates": [],
            "round": null,
            "roundsPlayed": 0,
            "turnedDown": 0,
            "askingMore": null,
            "pick": null,
            "hiredAt": null,
            "direct": null,
            "backups": [],
            "alternatives": [],
            "explanation": null,
            "canReview": false,
            "reviewed": false,
            "updatedAt": "2026-11-17T12:11:05.382Z"
          }
        }
    """.trimIndent()

    val PingingJob = """
        {
          "job": {
            "id": 4537,
            "rawText": "Kitchen tap is dripping, need a plumber",
            "summary": "Dripping kitchen tap needs repair",
            "category": {
              "id": 2,
              "slug": "plumber",
              "nameEn": "Plumber",
              "nameRw": "Umufundi w'amazi",
              "nameFr": "Plombier",
              "icon": "Droplets",
              "listed": true
            },
            "requirements": [
              {
                "label": "Fix dripping kitchen tap",
                "importance": "must"
              }
            ],
            "urgency": "flexible",
            "lat": -1.9536,
            "lng": 30.0606,
            "addressLabel": "Kiyovu",
            "budgetRwf": 15000,
            "status": "open",
            "notifyMe": false,
            "sessionId": 4534,
            "createdAt": "2026-11-17T23:31:38.462Z"
          },
          "session": {
            "id": 4534,
            "jobId": 4537,
            "state": "pinging",
            "stats": {
              "pinged": 0,
              "seen": 0,
              "available": 0,
              "declined": 0,
              "wave": 2,
              "radiusKm": 6,
              "firstReplySeconds": null,
              "typicalReplySeconds": 178
            },
            "replies": [],
            "shortlistDueAt": "2026-11-17T23:31:43.464Z",
            "candidates": [],
            "round": null,
            "roundsPlayed": 0,
            "turnedDown": 0,
            "askingMore": null,
            "pick": null,
            "hiredAt": null,
            "direct": null,
            "backups": [],
            "alternatives": [],
            "explanation": null,
            "canReview": false,
            "reviewed": false,
            "updatedAt": "2026-11-17T23:31:40.170Z"
          }
        }
    """.trimIndent()

    val RequestList = """
        [
          {
            "id": 4537,
            "summary": "Dripping kitchen tap needs repair",
            "categoryName": "Plumber",
            "categoryIcon": "Droplets",
            "status": "cancelled",
            "state": "cancelled",
            "budgetRwf": 15000,
            "addressLabel": "Kiyovu",
            "createdAt": "2026-11-17T23:31:38.462Z"
          },
          {
            "id": 4524,
            "summary": "Security guard service requested, details unclear",
            "categoryName": "Other services",
            "categoryIcon": "Wrench",
            "status": "no_providers",
            "state": "no_providers",
            "budgetRwf": null,
            "addressLabel": "My location",
            "createdAt": "2026-11-17T12:09:24.791Z"
          }
        ]
    """.trimIndent()
}
