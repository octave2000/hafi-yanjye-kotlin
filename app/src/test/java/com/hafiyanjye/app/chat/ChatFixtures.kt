package com.hafiyanjye.app.chat

/**
 * Real answers from the local engine (demo job provider, user 1, chatting with
 * demo job seeker Kevin, user 19), trimmed to a few items. Captured 2026-11-17.
 */
internal object ChatFixtures {
    fun envelope(data: String) =
        """{"ok":true,"data":$data,"meta":{"requestId":"test","timestamp":"2026-11-17T23:31:40.000Z"}}"""

    val CONVERSATIONS = """
        {
          "items": [
            {
              "id": 10,
              "other": {
                "userId": 19,
                "name": "Kevin Habiyaremye",
                "picture": null,
                "phone": null
              },
              "context": null,
              "hire": {
                "providerId": 18,
                "displayName": "Kevin Habiyaremye",
                "categories": [
                  {
                    "slug": "tutor",
                    "nameEn": "Tutor"
                  },
                  {
                    "slug": "it_repair",
                    "nameEn": "Computer & IT repair"
                  }
                ]
              },
              "lastMessage": {
                "id": 57,
                "conversationId": 10,
                "senderId": 19,
                "kind": "text",
                "body": "Yes, Saturday 9:00 works. Chemistry and maths, 2 hours.",
                "attachment": null,
                "createdAt": "2026-11-17T23:31:37.489Z"
              },
              "unread": 1,
              "otherReadId": 56,
              "updatedAt": "2026-11-17T23:31:37.489Z"
            },
            {
              "id": 9,
              "other": {
                "userId": 427,
                "name": "Olivier Niyonzima",
                "picture": null,
                "phone": "+250788000101"
              },
              "context": {
                "type": "listing",
                "id": 75,
                "kind": "car",
                "title": "Toyota RAV4, clean and fuel efficient",
                "photo": "/api/media/abb53bd2-397d-45fb-8ef2-4502bdefb419"
              },
              "hire": null,
              "lastMessage": {
                "id": 53,
                "conversationId": 9,
                "senderId": 1,
                "kind": "file",
                "body": "",
                "attachment": {
                  "url": "/api/chat/files/747f6d23-b24c-4d5b-8079-2ed689c0b058",
                  "name": "quote.pdf",
                  "size": 44,
                  "mime": "application/pdf",
                  "width": null,
                  "height": null
                },
                "createdAt": "2026-11-15T20:23:09.628Z"
              },
              "unread": 2,
              "otherReadId": 49,
              "updatedAt": "2026-11-15T20:23:09.628Z"
            },
            {
              "id": 5,
              "other": {
                "userId": 260,
                "name": "Divine Uwineza",
                "picture": null,
                "phone": "+250788123441"
              },
              "context": {
                "type": "job",
                "id": 4498,
                "title": "The seeker needs a plumber, with no further details given.",
                "mine": true
              },
              "hire": null,
              "lastMessage": {
                "id": 21,
                "conversationId": 5,
                "senderId": 1,
                "kind": "text",
                "body": "hello",
                "attachment": null,
                "createdAt": "2026-11-14T13:28:06.443Z"
              },
              "unread": 0,
              "otherReadId": null,
              "updatedAt": "2026-11-14T13:28:06.443Z"
            }
          ],
          "unread": 3
        }
    """.trimIndent()

    val THREAD = """
        {
          "conversation": {
            "id": 10,
            "other": {
              "userId": 19,
              "name": "Kevin Habiyaremye",
              "picture": null,
              "phone": null
            },
            "context": null,
            "hire": {
              "providerId": 18,
              "displayName": "Kevin Habiyaremye",
              "categories": [
                {
                  "slug": "tutor",
                  "nameEn": "Tutor"
                },
                {
                  "slug": "it_repair",
                  "nameEn": "Computer & IT repair"
                }
              ]
            },
            "lastMessage": {
              "id": 57,
              "conversationId": 10,
              "senderId": 19,
              "kind": "text",
              "body": "Yes, Saturday 9:00 works. Chemistry and maths, 2 hours.",
              "attachment": null,
              "createdAt": "2026-11-17T23:31:37.489Z"
            },
            "unread": 1,
            "otherReadId": 57,
            "updatedAt": "2026-11-17T23:31:37.489Z"
          },
          "items": [
            {
              "id": 54,
              "conversationId": 10,
              "senderId": 1,
              "kind": "text",
              "body": "Hello Kevin, do you also teach chemistry?",
              "attachment": null,
              "createdAt": "2026-11-16T10:36:20.290Z"
            },
            {
              "id": 56,
              "conversationId": 10,
              "senderId": 1,
              "kind": "text",
              "body": "Hi Kevin, are you free on Saturday morning for a chemistry lesson?",
              "attachment": null,
              "createdAt": "2026-11-17T23:31:27.129Z"
            },
            {
              "id": 57,
              "conversationId": 10,
              "senderId": 19,
              "kind": "text",
              "body": "Yes, Saturday 9:00 works. Chemistry and maths, 2 hours.",
              "attachment": null,
              "createdAt": "2026-11-17T23:31:37.489Z"
            }
          ],
          "hasMore": false
        }
    """.trimIndent()

    val THREAD_ATTACHMENTS = """
        {
          "conversation": {
            "id": 9,
            "other": {
              "userId": 427,
              "name": "Olivier Niyonzima",
              "picture": null,
              "phone": "+250788000101"
            },
            "context": {
              "type": "listing",
              "id": 75,
              "kind": "car",
              "title": "Toyota RAV4, clean and fuel efficient",
              "photo": "/api/media/abb53bd2-397d-45fb-8ef2-4502bdefb419"
            },
            "hire": null,
            "lastMessage": null,
            "unread": 0,
            "otherReadId": 49,
            "updatedAt": "2026-11-15T20:23:09.628Z"
          },
          "items": [
            {
              "id": 52,
              "conversationId": 9,
              "senderId": 1,
              "kind": "image",
              "body": "",
              "attachment": {
                "url": "/api/chat/files/cd37b73f-3f3a-4d99-a05d-7ab47c9bc758",
                "name": "Photo 3.jpg",
                "size": 4816,
                "mime": "image/jpeg",
                "width": null,
                "height": null
              },
              "createdAt": "2026-11-15T20:23:09.132Z"
            },
            {
              "id": 53,
              "conversationId": 9,
              "senderId": 1,
              "kind": "file",
              "body": "",
              "attachment": {
                "url": "/api/chat/files/747f6d23-b24c-4d5b-8079-2ed689c0b058",
                "name": "quote.pdf",
                "size": 44,
                "mime": "application/pdf",
                "width": null,
                "height": null
              },
              "createdAt": "2026-11-15T20:23:09.628Z"
            }
          ],
          "hasMore": true
        }
    """.trimIndent()

    val SENT = """
        {
          "id": 57,
          "conversationId": 10,
          "senderId": 19,
          "kind": "text",
          "body": "Yes, Saturday 9:00 works. Chemistry and maths, 2 hours.",
          "attachment": null,
          "createdAt": "2026-11-17T23:31:37.489Z"
        }
    """.trimIndent()

    val NOT_FOUND = """
        {
          "ok": false,
          "error": {
            "code": "NOT_FOUND",
            "message": "Conversation not found"
          },
          "meta": {
            "requestId": "test",
            "timestamp": "2026-11-17T23:31:27.735Z"
          }
        }
    """.trimIndent()
}
