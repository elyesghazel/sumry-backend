package ch.sumry.core.module.porting

import ch.sumry.core.exception.RequestException
import ch.sumry.core.privacy.maskAccounts
import org.springframework.http.HttpStatus
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.Base64

/**
 * Turns an import request into parsed rows, whatever was sent: pasted CSV text (the
 * original shape) or an uploaded file as base64 (PDF, camt XML or CSV). The file is only
 * ever held in memory for the request; nothing of it is stored or logged.
 */
object StatementReader {

    const val MAX_FILE_BYTES = 10 * 1024 * 1024
    /** Longest note the transactions table holds. */
    private const val MAX_DESCRIPTION = 255

    private val pdfFormats = setOf("postfinance-pdf", "pdf")
    private val camtFormats = setOf("camt053", "camt052", "camt054", "camt", "camt.053", "camt.052", "camt.054", "xml")

    fun read(req: ImportPreviewRequest): ParseResult {
        val format = req.format?.trim()?.lowercase()?.takeIf { it.isNotEmpty() && it != "auto" }
        val file = req.fileBase64?.takeIf { it.isNotBlank() }
        val result = when {
            file != null -> readFile(decode(file), format, req.fileName)
            !req.csv.isNullOrBlank() -> when {
                format in pdfFormats -> throw RequestException(HttpStatus.BAD_REQUEST, "Send a PDF as fileBase64, not as csv text")
                format in camtFormats || (format == null && CamtParser.looksLikeCamt(req.csv)) ->
                    CamtParser.parse(req.csv.toByteArray(Charsets.UTF_8))
                else -> CsvParsers.parse(req.csv, format)
            }
            else -> throw RequestException(HttpStatus.BAD_REQUEST, "csv is empty")
        }
        return masked(result)
    }

    private fun readFile(bytes: ByteArray, format: String?, fileName: String?): ParseResult {
        val name = fileName?.lowercase().orEmpty()
        val isPdf = PdfStatementParser.isPdf(bytes)
        if (format in pdfFormats || (format == null && isPdf)) {
            if (!isPdf) throw RequestException(HttpStatus.BAD_REQUEST, "The file is not a PDF")
            return PdfStatementParser.parse(bytes)
        }
        if (isPdf) throw RequestException(HttpStatus.BAD_REQUEST, "The file is a PDF; use format postfinance-pdf or auto")
        val text = text(bytes)
        if (format in camtFormats || (format == null && (CamtParser.looksLikeCamt(text) || name.endsWith(".xml") || text.trimStart().startsWith("<?xml")))) {
            return CamtParser.parse(bytes)
        }
        return CsvParsers.parse(text, format)
    }

    private fun decode(base64: String): ByteArray {
        // Base64 grows the file by a third; refuse before decoding anything that large.
        val body = base64.substringAfter("base64,", base64).filterNot { it.isWhitespace() }
        if (body.length > MAX_FILE_BYTES / 3 * 4 + 8) tooLarge()
        val bytes = try {
            Base64.getDecoder().decode(body)
        } catch (_: IllegalArgumentException) {
            throw RequestException(HttpStatus.BAD_REQUEST, "fileBase64 is not valid base64")
        }
        if (bytes.size > MAX_FILE_BYTES) tooLarge()
        if (bytes.isEmpty()) throw RequestException(HttpStatus.BAD_REQUEST, "The file is empty")
        return bytes
    }

    private fun tooLarge(): Nothing =
        throw RequestException(HttpStatus.PAYLOAD_TOO_LARGE, "The file is larger than ${MAX_FILE_BYTES / 1024 / 1024} MB")

    /** UTF-8 when the bytes are valid UTF-8, otherwise Windows-1252, which older Swiss bank exports use. */
    private fun text(bytes: ByteArray): String = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString()
    } catch (_: CharacterCodingException) {
        String(bytes, Charset.forName("windows-1252"))
    }

    /** No full account number leaves the import, whichever format it came from. */
    private fun masked(r: ParseResult): ParseResult = r.copy(
        rows = r.rows.map {
            it.copy(
                description = maskAccounts(it.description).take(MAX_DESCRIPTION),
                rawDescription = maskAccounts(it.rawDescription),
            )
        },
        warnings = r.warnings.map { maskAccounts(it) },
    )
}
