package gh.ug.kasacore.network

import gh.ug.kasacore.model.Intent
import okhttp3.MultipartBody
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

/*
 * The App <-> Server contract — 00_START_HERE.md §4.2. No contacts payload:
 * per K05 Decision 3 the server never receives them (see the drift note in
 * 01_BACKEND_GUIDE.md / 06_K05_DESIGN_DECISIONS.md, now fixed on both ends).
 */
interface KasaApi {
    @Multipart
    @POST("understand")
    suspend fun understand(@Part audio: MultipartBody.Part): Intent

    @GET("health")
    suspend fun health(): Map<String, String>
}
