package com.sagar.voice_shield.data.remote

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.sagar.voice_shield.data.remote.dto.HfAudioAnalysisResponse
import com.sagar.voice_shield.data.remote.dto.HfHealthResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader

/**
 * Direct client for Hugging Face Gradio 4/5/6 Space API:
 * Space: https://shauriya24-voiceshield.hf.space
 *
 * Implements the 3-step Gradio REST protocol:
 * 1. POST /gradio_api/upload -> Uploads WAV file (form field 'files')
 * 2. POST /gradio_api/call/analyze_audio -> Initiates AASIST model inference
 * 3. GET  /gradio_api/call/analyze_audio/{event_id} -> Streams SSE until completion and extracts AASIST result
 *
 * Automatically falls back to VoiceShield Render backend (/api/analysis/upload)
 * if Hugging Face Space is sleeping or timing out.
 */
class HuggingFaceGradioClient(
    private val client: OkHttpClient,
    private val baseUrl: String = "https://shauriya24-voiceshield.hf.space",
    private val backendApi: VoiceShieldApi? = null
) {
    private val TAG = "HfGradioClient"
    private val gson = Gson()
    private val cleanBase = baseUrl.trimEnd('/')

    /**
     * Upload and analyze a WAV audio chunk with the AASIST AI model on Hugging Face Space.
     */
    suspend fun analyzeAudio(wavBytes: ByteArray): HfAudioAnalysisResponse = withContext(Dispatchers.IO) {
        try {
            // Attempt direct Hugging Face Space Gradio inference
            return@withContext callGradioAasist(wavBytes)
        } catch (hfError: Exception) {
            Log.w(TAG, "Hugging Face Gradio call failed (${hfError.message}). Attempting Render backend fallback.", hfError)

            // Fallback to Render backend (/api/analysis/upload)
            if (backendApi != null) {
                try {
                    val requestBody = wavBytes.toRequestBody("audio/wav".toMediaTypeOrNull())
                    val part = MultipartBody.Part.createFormData("file", "chunk.wav", requestBody)
                    val backendResp = backendApi.uploadAudio(part)
                    Log.d(TAG, "Render backend fallback succeeded. Risk: ${backendResp.riskScore}, Deepfake: ${backendResp.deepfakeScore}")
                    return@withContext HfAudioAnalysisResponse(
                        status = "success",
                        label = if (backendResp.deepfakeScore > 0.5) "SPOOF (Deepfake Detected)" else "BONAFIDE (Genuine)",
                        isDeepfake = backendResp.deepfakeScore > 0.5,
                        deepfakeScore = backendResp.deepfakeScore,
                        bonafideScore = 1.0 - backendResp.deepfakeScore,
                        speakerSimilarity = backendResp.speakerSimilarity ?: backendResp.speakerScore ?: 0.75,
                        prosodyScore = backendResp.prosodyScore,
                        contextScore = backendResp.contextScore,
                        riskScore = backendResp.riskScore,
                        severity = backendResp.severity
                    )
                } catch (backendError: Exception) {
                    Log.e(TAG, "Render backend fallback also failed", backendError)
                    throw backendError
                }
            } else {
                throw hfError
            }
        }
    }

    private fun callGradioAasist(wavBytes: ByteArray): HfAudioAnalysisResponse {
        // Step 1: Upload WAV file to /gradio_api/upload
        val uploadUrl = "$cleanBase/gradio_api/upload"
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "files",
                "audio_chunk.wav",
                wavBytes.toRequestBody("audio/wav".toMediaTypeOrNull())
            )
            .build()

        val uploadRequest = Request.Builder()
            .url(uploadUrl)
            .post(requestBody)
            .build()

        val uploadResp = client.newCall(uploadRequest).execute()
        if (!uploadResp.isSuccessful) {
            val err = uploadResp.body?.string()
            uploadResp.close()
            throw IOException("Upload failed with HTTP ${uploadResp.code}: $err")
        }

        val uploadJsonStr = uploadResp.body?.string() ?: throw IOException("Empty upload response")
        val uploadedFiles = gson.fromJson(uploadJsonStr, Array<String>::class.java)
        if (uploadedFiles.isNullOrEmpty()) {
            throw IOException("No file path returned in upload response: $uploadJsonStr")
        }
        val serverFilePath = uploadedFiles[0]
        Log.d(TAG, "Uploaded audio chunk to HF Gradio path: $serverFilePath")

        // Step 2: Trigger /gradio_api/call/analyze_audio
        val callUrl = "$cleanBase/gradio_api/call/analyze_audio"
        val payload = JsonObject().apply {
            val dataArray = JsonArray()
            val fileObj = JsonObject().apply {
                addProperty("path", serverFilePath)
                val metaObj = JsonObject().apply {
                    addProperty("_type", "gradio.FileData")
                }
                add("meta", metaObj)
            }
            dataArray.add(fileObj)
            add("data", dataArray)
        }

        val callRequest = Request.Builder()
            .url(callUrl)
            .post(payload.toString().toRequestBody("application/json".toMediaTypeOrNull()))
            .build()

        val callResp = client.newCall(callRequest).execute()
        if (!callResp.isSuccessful) {
            val err = callResp.body?.string()
            callResp.close()
            throw IOException("Gradio call failed with HTTP ${callResp.code}: $err")
        }

        val callJsonStr = callResp.body?.string() ?: throw IOException("Empty call response")
        val callJson = gson.fromJson(callJsonStr, JsonObject::class.java)
        val eventId = callJson.get("event_id")?.asString ?: throw IOException("No event_id returned: $callJsonStr")
        Log.d(TAG, "Gradio event ID: $eventId")

        // Step 3: Stream SSE from /gradio_api/call/analyze_audio/{eventId}
        val sseUrl = "$cleanBase/gradio_api/call/analyze_audio/$eventId"
        val sseRequest = Request.Builder()
            .url(sseUrl)
            .get()
            .build()

        val sseResp = client.newCall(sseRequest).execute()
        if (!sseResp.isSuccessful) {
            val err = sseResp.body?.string()
            sseResp.close()
            throw IOException("SSE stream failed with HTTP ${sseResp.code}: $err")
        }

        val reader = BufferedReader(InputStreamReader(sseResp.body?.byteStream() ?: throw IOException("No SSE body stream")))
        var resultResponse: HfAudioAnalysisResponse? = null

        try {
            var line: String? = reader.readLine()
            while (line != null) {
                if (line.startsWith("data:")) {
                    val dataJsonStr = line.substring(5).trim()
                    try {
                        val rootArray = gson.fromJson(dataJsonStr, JsonArray::class.java)
                        // Gradio returns: [markdown, detection_probs, acoustic_scores, raw_json]
                        for (element in rootArray) {
                            if (element.isJsonObject) {
                                val obj = element.asJsonObject
                                if (obj.has("deepfake_score") || obj.has("risk_score")) {
                                    resultResponse = gson.fromJson(obj, HfAudioAnalysisResponse::class.java)
                                    Log.d(TAG, "Parsed AASIST response: risk=${resultResponse.riskScore}, deepfake=${resultResponse.deepfakeScore}")
                                    break
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.v(TAG, "Non-array SSE data: $dataJsonStr")
                    }
                }
                if (resultResponse != null) break
                line = reader.readLine()
            }
        } finally {
            try { reader.close() } catch (_: Exception) {}
            try { sseResp.close() } catch (_: Exception) {}
        }

        return resultResponse ?: throw IOException("No valid analysis result found in SSE stream")
    }

    /**
     * Check health of Hugging Face Space via /gradio_api/info
     */
    suspend fun healthCheck(): HfHealthResponse = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url("$cleanBase/gradio_api/info").get().build()
            val resp = client.newCall(req).execute()
            val isOk = resp.isSuccessful
            resp.close()
            HfHealthResponse(status = if (isOk) "ok" else "error")
        } catch (e: Exception) {
            HfHealthResponse(status = "unreachable")
        }
    }
}
