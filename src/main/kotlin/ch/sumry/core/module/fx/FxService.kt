package ch.sumry.core.module.fx

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant

data class FxRates(
    val base: String,
    /** Units of each currency per one unit of [base]. */
    val rates: Map<String, BigDecimal>,
    val fetchedAt: Instant,
    /** True when these rates could not be refreshed and are older than the cache window. */
    val stale: Boolean,
)

/**
 * Daily reference rates from the ECB via frankfurter.dev, cached in Redis. When the
 * fetch fails the last good set is used and flagged stale; only when there has never
 * been one does conversion fall back to 1:1, and then the flag says so.
 */
@Service
class FxService(
    private val redis: StringRedisTemplate,
    private val mapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val http: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NORMAL).build()

    fun rates(base: String = "CHF"): FxRates {
        val b = base.uppercase()
        cached(freshKey(b))?.let { return it }
        fetch(b)?.let {
            store(it)
            return it
        }
        cached(lastKey(b))?.let { return it.copy(stale = true) }
        return FxRates(b, emptyMap(), Instant.EPOCH, stale = true)
    }

    /** Rate that turns one unit of [from] into [to]; 1 when they are the same or unknown. */
    fun rate(from: String, to: String): BigDecimal {
        val f = from.uppercase(); val t = to.uppercase()
        if (f == t) return BigDecimal.ONE
        val r = rates(t)
        // rates(t) gives units of `f` per one `t`, so one `f` is 1/that many `t`.
        val perBase = r.rates[f] ?: return BigDecimal.ONE
        if (perBase.signum() == 0) return BigDecimal.ONE
        return BigDecimal.ONE.divide(perBase, 6, RoundingMode.HALF_UP)
    }

    fun convert(amount: BigDecimal, from: String, to: String): BigDecimal =
        amount.multiply(rate(from, to)).setScale(2, RoundingMode.HALF_UP)

    private fun fetch(base: String): FxRates? = try {
        val req = HttpRequest.newBuilder(URI.create("https://api.frankfurter.dev/v1/latest?base=$base"))
            .timeout(Duration.ofSeconds(8)).header("User-Agent", "sumry/1.0").GET().build()
        val res = http.send(req, HttpResponse.BodyHandlers.ofString())
        if (res.statusCode() != 200) throw IllegalStateException("HTTP ${res.statusCode()}")
        val node = mapper.readTree(res.body())
        val rates = node["rates"].fields().asSequence().associate { (k, v) -> k to BigDecimal(v.asText()) }
        FxRates(base, rates, Instant.now(), stale = false)
    } catch (ex: Exception) {
        log.warn("FX refresh failed for {}: {}", base, ex.message)
        null
    }

    private fun cached(key: String): FxRates? = try {
        redis.opsForValue().get(key)?.let { mapper.readValue(it, FxRates::class.java) }
    } catch (ex: Exception) {
        null
    }

    private fun store(r: FxRates) = try {
        val json = mapper.writeValueAsString(r)
        redis.opsForValue().set(freshKey(r.base), json, Duration.ofHours(12))
        redis.opsForValue().set(lastKey(r.base), json)
    } catch (ex: Exception) {
        log.warn("Could not cache FX rates: {}", ex.message)
    }

    private fun freshKey(base: String) = "fx:fresh:$base"
    private fun lastKey(base: String) = "fx:last:$base"
}
