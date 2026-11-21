package com.hafiyanjye.app.location

/** Real answers captured on 2026-11-18 (Kigali centre to Kimironko market), trimmed to the fields the app reads. */
internal object Fixtures {
    /** Routes API `computeRoutes`, TWO_WHEELER, field mask distanceMeters,duration,polyline.encodedPolyline. */
    val RoutePolyline = listOf(
        "nvzJuknvD^QTWFe@C_@EKp@gA^_A^eBf@sELwAn@kCfAiDTy@d@wAnBwG|@gDv@{Bp@yAf@{@f@m@v@q@hAy@lBkAh",
        "B_AbGoCrCcAdA]~AUx@GlBEASEUMU_@Uc@OkGu@iFa@aAU_@Mq@a@kBkB_@Yy@e@}@So@EeCTqBVoA@mASc@Ui@[WY",
        "Yc@Sq@GUEq@FaAbAgHTmAn@kCjBkExJuHzCyB`CaCdAkAnCiDaA{@Q[KYEY?_ALiBj@cGnAoFf@wArA_Dv@sArD}EH",
        "c@A_@Qi@i@a@i@CuMKOEEIA]BUTg@d@i@r@[zAa@bAe@j@_@h@i@z@gAbAgAz@q@pAs@|@]ZIRARJV@RIJS?WKQQIE",
        "?IOKa@cAsI}@yGMkAKeCDyBJoARcATo@Vc@d@k@r@m@z@m@rBcAnIoDpEyBxAaAjBiBbAuAp@sAl@oAp@aCd@aDZuC",
        "PIHSCSIKKCWBMNAFSIg@_@[c@y@wAuBqCg@cAcHaVyCqJe@_B_@}@KM_GaDa@_@aUk]iDmF}@mAa@{@gBaEj@aA|B|",
        "A",
    ).joinToString("")

    val Route = """{"routes":[{"distanceMeters":10220,"duration":"1288s",""" +
        """"polyline":{"encodedPolyline":"$RoutePolyline"}}]}"""

    /** Places API (New) `places:autocomplete` for "Kimironko mar", first two suggestions. */
    val Autocomplete = """{"suggestions":[
        {"placePrediction":{"place":"places/ChIJqcGXxg-n3BkRxuHf9tGbnhA","placeId":"ChIJqcGXxg-n3BkRxuHf9tGbnhA",
        "text":{"text":"Kimironko Market, Kigali, Rwanda","matches":[{"endOffset":13}]},
        "structuredFormat":{"mainText":{"text":"Kimironko Market","matches":[{"endOffset":13}]},
        "secondaryText":{"text":"Kigali, Rwanda"}},"types":["premise","geocode"]}},
        {"placePrediction":{"place":"places/ChIJ6Wou6w-n3BkRV1pEE35WyUI","placeId":"ChIJ6Wou6w-n3BkRV1pEE35WyUI",
        "text":{"text":"Kimironko Market, KG 145 Street, Kigali, Rwanda","matches":[{"endOffset":13}]},
        "structuredFormat":{"mainText":{"text":"Kimironko Market","matches":[{"endOffset":13}]},
        "secondaryText":{"text":"KG 145 Street, Kigali, Rwanda"}},"types":["market","establishment"]}}]}"""

    /** Places API (New) place details, field mask displayName,location. */
    val Place = """{"location":{"latitude":-1.9498217,"longitude":30.1261802},
        "displayName":{"text":"Kimironko Market","languageCode":"en"}}"""

    /** Nominatim `search?format=jsonv2` for "Kimironko market", Rwanda only. */
    val Nominatim = """[{"place_id":38506438,"licence":"Data © OpenStreetMap contributors, ODbL 1.0.",
        "osm_type":"way","osm_id":106506354,"lat":"-1.9497712","lon":"30.1262879","category":"amenity",
        "type":"marketplace","place_rank":30,"addresstype":"amenity","name":"Kimironko Market",
        "display_name":"Kimironko Market, KG 161 Street, Kimironko, Gasabo District, City of Kigali, Rwanda",
        "boundingbox":["-1.9504649","-1.9492747","30.1257429","30.1266978"]}]"""

    /** Nominatim when nothing in Rwanda matches. */
    const val NominatimEmpty = "[]"
}
