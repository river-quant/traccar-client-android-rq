package org.traccar.client

import android.app.ProgressDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import com.google.android.material.snackbar.Snackbar
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Request.Builder
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.Executors

class LoginActivity : AppCompatActivity() {
    private var userPublicId: EditText? = null
    private var deviceName: EditText? = null
    private var devicePhone: EditText? = null
    private var submitBtn: Button? = null
    private var progressDialog: ProgressDialog? = null
    private var rootView: View? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val deviceUniqueId = sharedPreferences.getString(MainFragment.KEY_DEVICE, "")
        Log.i("LoginActivity", deviceUniqueId!!)
        if (deviceUniqueId != "") {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent);
        }
        setContentView(R.layout.login)
        userPublicId = findViewById(R.id.userPublicId)
        deviceName = findViewById(R.id.deviceName)
        devicePhone = findViewById(R.id.devicePhone)
        submitBtn = findViewById(R.id.btnSubmit)
        progressDialog = ProgressDialog(this)
        rootView = window.decorView.rootView
        submitBtn?.setOnClickListener {
            // Validate data in textboxes
            if (validateData()) {
                // Show progress dialog
                showProgressDialog()

                // Call API using ExecutorService
                // Note: In a real-world scenario, you may want to manage the executor lifecycle appropriately
                val executorService = Executors.newSingleThreadExecutor()
                executorService.submit(APICallTask())
            }
        }
    }

    private fun validateData(): Boolean {
        return true
    }

    private fun showProgressDialog() {
        progressDialog!!.setMessage("Calling API...")
        progressDialog!!.setCancelable(false)
        progressDialog!!.show()
    }

    private fun dismissProgressDialog() {
        progressDialog!!.dismiss()
    }

    private fun handleResponse(response: Response, responseStr: String) {
        try {
            val isSuccessful = response.isSuccessful
            Log.i("LoginActivity-success", isSuccessful.toString())
            Log.i("LoginActivity-resCode", response.code.toString())
            if (!isSuccessful) {
                if (response.code == 404) {
                    userPublicId!!.error = "Invalid Client ID"
                } else {
                    Snackbar.make(
                        rootView!!,
                        "Login Error: " + response.code,
                        Snackbar.LENGTH_SHORT
                    ).show()
                }
            } else {
                Log.i("LoginActivity", responseStr)
                val responseJson = JSONObject(responseStr)
                val deviceUniqueId = responseJson.getString("uniqueId")
                Log.i("LoginActivity", deviceUniqueId)
                val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
                val editor = sharedPreferences.edit()
                editor.putString(MainFragment.KEY_DEVICE, deviceUniqueId)
                editor.apply()
                val intent = Intent(this, MainActivity::class.java)
                startActivity(intent)
            }
        } catch (e: Exception) {
            Log.i("LoginActivity", e.toString())
            Snackbar.make(rootView!!, e.toString(), Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun handleNoResponse(e: Exception) {
        Log.i("LoginActivity-NoRes", e.toString())
        val rootView = window.decorView.rootView
        Snackbar.make(rootView, e.toString(), Snackbar.LENGTH_INDEFINITE).show()
    }

    private inner class APICallTask : Runnable {
        override fun run() {
            val serverUrl = this@LoginActivity.getString(R.string.settings_url_default_value)
            val apiUrl = String.format("%s/api/devices/public/%s", serverUrl, userPublicId!!.text)
            val bodyStr = String.format(
                "{ \"name\": \"%s\", \"phone\": \"%s\" }",
                deviceName!!.text,
                devicePhone!!.text
            )
            try {
                post(apiUrl, bodyStr).use { response ->
                    assert(response.body != null)
                    val responseStr = response.body!!.string()
                    runOnUiThread {
                        try {
                            handleResponse(response, responseStr)
                        } catch (e: Exception) {
                            handleNoResponse(e)
                        }
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { handleNoResponse(e) }
            } finally {
                runOnUiThread { dismissProgressDialog() }
            }
        }

        @Throws(IOException::class)
        fun post(url: String, json: String): Response {
            val JSON: MediaType = "application/json".toMediaType()
            val client = OkHttpClient()

            val body: RequestBody = json.toRequestBody(JSON)
            val request: Request = Builder()
                .url(url)
                .post(body)
                .build()
            return client.newCall(request).execute()
        }
    }
}
