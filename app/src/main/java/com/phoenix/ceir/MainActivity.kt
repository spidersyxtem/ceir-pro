package com.phoenix.ceir

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.webkit.*
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var resultLayout: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        resultLayout = findViewById(R.id.resultLayout)

        setupWebView()

        findViewById<Button>(R.id.btnBack).setOnClickListener {
            resultLayout.visibility = View.GONE
            webView.visibility = View.VISIBLE
            webView.reload()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.addJavascriptInterface(this, "AndroidBridge")
        
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                // Monitor the result table
                val script = """
                    var check = setInterval(function() {
                        var table = document.querySelector('table');
                        if (table && table.innerText.includes('Brand')) {
                            var b = table.rows[0].cells[1].innerText;
                            var m = table.rows[1].cells[1].innerText;
                            var d = table.rows[2].cells[1].innerText;
                            AndroidBridge.onResult(b, m, d);
                            clearInterval(check);
                        }
                    }, 1000);
                """.trimIndent()
                webView.evaluateJavascript(script, null)
            }
        }
        webView.loadUrl("https://ceir.gov.mm/check-status")
    }

    @JavascriptInterface
    fun onResult(brand: String, model: String, date: String) {
        runOnUiThread {
            webView.visibility = View.GONE
            resultLayout.visibility = View.VISIBLE
            
            findViewById<TextView>(R.id.txtInfo).text = "Device: $brand $model"
            val status = findViewById<TextView>(R.id.txtStatus)
            
            if (date.contains("2024") && !date.contains("Jan") && !date.contains("Feb") && !date.contains("Mar")) {
                status.text = "AFTER APRIL 2024 (TAX REQUIRED)"
                status.setTextColor(Color.RED)
            } else {
                status.text = "BEFORE APRIL 2024 (CLEAN)"
                status.setTextColor(Color.GREEN)
            }
        }
    }
}
