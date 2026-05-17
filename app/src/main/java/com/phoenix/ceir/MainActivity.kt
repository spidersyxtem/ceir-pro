            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: ""
                val b64 = Base64.encodeToString(body.toByteArray(), Base64.NO_WRAP)
                callJs("receiveChallenge('" + b64 + "')")
            }
        })
    }

    private fun solveAltcha(salt: String, challenge: String, maxNumber: Long) {
        Thread {
            val md = MessageDigest.getInstance("SHA-256")
            var solved = -1L
            for (n in 0..maxNumber) {
                val hash = md.digest((salt + n).toByteArray())
                    .joinToString("") { "%02x".format(it) }
                if (hash == challenge) { solved = n; break }
            }
            callJs("submitVerification(" + solved + ")")
        }.start()
    }

    private fun verifyImei(b64Payload: String, imei: String) {
        val cookies = CookieManager.getInstance().getCookie("https://ceir.gov.mm") ?: ""
        val payloadStr = String(Base64.decode(b64Payload, Base64.DEFAULT))
        val altchaToken = java.net.URLEncoder.encode(payloadStr, "UTF-8")

        val verifyBody = JSONObject().put("imei", imei).toString()
            .toRequestBody("application/json".toMediaType())

        val verifyReq = Request.Builder()
            .url("https://ceir.gov.mm/openapi/API/IMEI/Verify?altcha=" + altchaToken + "&imei=" + imei)
            .post(verifyBody)
            .header("User-Agent", userAgent)
            .header("Cookie", cookies)
            .header("Referer", "https://ceir.gov.mm/")
            .build()

        val deviceReq = Request.Builder()
            .url("https://ceir.gov.mm/openapi/API/Device/personal-device-info?altcha=" + altchaToken)
            .header("User-Agent", userAgent)
            .header("Cookie", cookies)
            .header("Referer", "https://ceir.gov.mm/")
            .build()

        client.newCall(verifyReq).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callJs("engineError('verify failed')")
            }
            override fun onResponse(call: Call, response: Response) {
                val vBody = response.body?.string() ?: ""
                val b64V = Base64.encodeToString(vBody.toByteArray(), Base64.NO_WRAP)
                client.newCall(deviceReq).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        val b64P = Base64.encodeToString("SKIP".toByteArray(), Base64.NO_WRAP)
                        callJs("receiveFinalResult('" + b64V + "','" + b64P + "')")
                    }
                    override fun onResponse(call: Call, response: Response) {
                        val pBody = response.body?.string() ?: "SKIP"
                        val b64P = Base64.encodeToString(pBody.toByteArray(), Base64.NO_WRAP)
                        callJs("receiveFinalResult('" + b64V + "','" + b64P + "')")
                    }
                })
            }
        })
    }

    private fun callJs(js: String) {
        mainHandler.post { webView.evaluateJavascript(js, null) }
    }
}
