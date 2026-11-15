package es.msaezo.iptv.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Where the internet sees this device (to check a VPN is doing its job). */
data class Location(val country: String, val countryCode: String, val city: String?, val ip: String, val isp: String?)

class NetInfo(private val scope: CoroutineScope) {
    private val _location = MutableStateFlow<Location?>(null)
    val location: StateFlow<Location?> = _location.asStateFlow()

    private val _checking = MutableStateFlow(false)
    val checking: StateFlow<Boolean> = _checking.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun refresh() {
        if (_checking.value) return
        _checking.value = true
        scope.launch(Dispatchers.IO) {
            try {
                _location.value = lookup()
                _error.value = null
            } catch (e: Exception) {
                _error.value = e.message ?: "lookup failed"
            } finally {
                _checking.value = false
            }
        }
    }

    private fun lookup(): Location {
        // Two independent public services; the second is only asked if the first fails.
        try {
            val o = JSONObject(Http.getText("https://ipwho.is/", UA))
            if (o.optBoolean("success", true) && o.optString("country").isNotBlank()) {
                return Location(
                    country = o.optString("country"),
                    countryCode = o.optString("country_code"),
                    city = o.optString("city").takeIf { it.isNotBlank() },
                    ip = o.optString("ip"),
                    isp = o.optJSONObject("connection")?.optString("isp")?.takeIf { it.isNotBlank() },
                )
            }
        } catch (_: Exception) {
        }
        val o = JSONObject(Http.getText("https://api.country.is/", UA))
        val code = o.optString("country")
        if (code.isBlank()) throw java.io.IOException("No answer from the location services")
        return Location(java.util.Locale("", code).displayCountry.ifBlank { code }, code, null, o.optString("ip"), null)
    }

    private companion object {
        const val UA = "TUTV/1.0"
    }
}
