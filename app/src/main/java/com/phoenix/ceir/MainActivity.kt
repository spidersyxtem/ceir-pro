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
    private var webView: WebView? = null
    private var resultLayout: LinearLayout? = null
    private var txtBrandInfo: TextView? = null
    private var txtAprilStatus: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        resultLayout = findViewById(R.id.resultLayout)
        txtBrandInfo = findViewById(R.id.txtBrandInfo)
        txtAprilStatus = findViewById(R.id.txtAprilStatus)
        val btnBack: Button = findViewById(R.id.btnBack)

        setupWebView()

        btnBack.setOnClickListener { showWebsite() }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val webSettings = webView?.settings
        webSettings?.javaScriptEnabled = true
        webSettings?.domStorageEnabled = true
        webSettings?.userAgentString = "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
        
        webView?.addJavascriptInterface(this, "AndroidBridge")
        
        webView?.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                // UI အသစ်၏ Elements များကို ပိုမိုတိကျစွာ ရှာဖွေမည်
                val script = """
                    (function() {
                        var checkResult = setInterval(function() {
                            var bodyText = document.body.innerText;
                            // 'Finished' ခလုတ် ပေါ်လာလျှင် အဖြေထွက်ပြီဟု သတ်မှတ်သည်
                            if (bodyText.includes('Finished')) {
                                
                                // ၁။ IMEI နံပါတ်ကို ရှာဖွေခြင်း (ပုံမှန်အားဖြင့် အပေါ်ဆုံးတွင် ရှိသည်)
                                var imeiText = "Unknown IMEI";
                                var allParagraphs = document.querySelectorAll('p');
                                for (var p of allParagraphs) {
                                    if (p.innerText.match(/^\d{15}/)) { // ၁၅ လုံးပါသော IMEI ကို ရှာသည်
                                        imeiText = p.innerText;
                                        break;
                                    }
                                }

                                // ၂။ Tax Status နှင့် Blocking Status ကို စာသားဖြင့် ရှာဖွေခြင်း
                                var isPaid = bodyText.includes('Paid');
                                var isAllowed = bodyText.includes('Allowed');

                                // ၃။ Data အားလုံးကို Android ဆီ ပို့လွှတ်ခြင်း
                                AndroidBridge.onDetailedResult(imeiText, isPaid, isAllowed);
                                clearInterval(checkResult);
                            }
                        }, 1000);
                    })();
                """.trimIndent()
                webView?.evaluateJavascript(script, null)
            }
        }
        webView?.loadUrl("https://ceir.gov.mm/check-status")
    }

    @JavascriptInterface
    fun onDetailedResult(imei: String, isPaid: Boolean, isAllowed: Boolean) {
        runOnUiThread {
            webView?.visibility = View.GONE
            resultLayout?.visibility = View.VISIBLE
            
            // အချက်အလက်များ အသေးစိတ် ပြသခြင်း
            val taxText = if (isPaid) "PAID ✅" else "UNPAID ❌"
            val blockText = if (isAllowed) "ALLOWED ✅" else "BLOCKED ❌"
            
            txtBrandInfo?.text = "IMEI: $imei\nTAX: $taxText\nSTATUS: $blockText"
            
            // Visual Alert Logic
            if (isPaid && isAllowed) {
                txtAprilStatus?.text = "VERIFIED: CLEAN DEVICE ✨"
                txtAprilStatus?.setBackgroundColor(Color.parseColor("#10B981")) // Green
            } else {
                txtAprilStatus?.text = "ALERT: CHECK TAX STATUS ⚠️"
                txtAprilStatus?.setBackgroundColor(Color.parseColor("#E11D48")) // Red
            }
            txtAprilStatus?.setTextColor(Color.WHITE)
        }
    }

    private fun showWebsite() {
        resultLayout?.visibility = View.GONE
        webView?.visibility = View.VISIBLE
        webView?.reload()
    }

    override fun onBackPressed() {
        if (resultLayout?.visibility == View.VISIBLE) showWebsite()
        else if (webView?.canGoBack() == true) webView?.goBack()
        else super.onBackPressed()
    }
}
