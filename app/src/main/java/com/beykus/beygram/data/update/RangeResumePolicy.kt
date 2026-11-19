package com.beykus.beygram.data.update

/** A ranged request for the rest of a partial file. It is sent only with a strong validator from the first attempt. */
internal data class ResumePlan(val offset: Long, val validator: String) {
    fun headers(): Map<String, String> = mapOf(
        "Range" to "bytes=$offset-",
        "If-Range" to validator,
    )
}

internal sealed interface ResumeDecision {
    /** 206 with exactly the bytes after [offset]: the body is appended to the partial file. */
    data class Append(val offset: Long) : ResumeDecision

    /** The whole file is coming (200): the partial is deleted and the body is written from zero. */
    data object FromStart : ResumeDecision

    /** 416 or a 206 that does not match the plan: the partial is deleted and the file is requested without a range. */
    data object RestartWithoutRange : ResumeDecision

    /** Any other reply is refused. */
    data object Reject : ResumeDecision
}

internal object RangeResumePolicy {
    private val contentRange = Regex("bytes (\\d+)-(\\d+)/(\\d+)")

    /** A plan only for a partial with some but not all bytes and a strong validator; weak ETags never resume. */
    fun plan(partialBytes: Long, validator: String?, expectedSize: Long): ResumePlan? {
        val strong = validator?.takeIf { it.isNotBlank() && !it.startsWith("W/") } ?: return null
        if (partialBytes <= 0L || partialBytes >= expectedSize) return null
        return ResumePlan(partialBytes, strong)
    }

    fun decide(
        plan: ResumePlan?,
        status: Int,
        contentRange: String?,
        responseValidator: String?,
        expectedSize: Long,
    ): ResumeDecision {
        if (plan == null) {
            return if (status == 200) ResumeDecision.FromStart else ResumeDecision.Reject
        }
        return when (status) {
            206 -> {
                val validatorOk = responseValidator == null || responseValidator == plan.validator
                if (validatorOk && matchesRange(contentRange, plan.offset, expectedSize)) {
                    ResumeDecision.Append(plan.offset)
                } else {
                    ResumeDecision.RestartWithoutRange
                }
            }
            200 -> ResumeDecision.FromStart
            416 -> ResumeDecision.RestartWithoutRange
            else -> ResumeDecision.Reject
        }
    }

    /** True only for `bytes offset-(expectedSize-1)/expectedSize`. */
    fun matchesRange(header: String?, offset: Long, expectedSize: Long): Boolean {
        val match = header?.trim()?.let { contentRange.matchEntire(it) } ?: return false
        val (start, end, total) = match.destructured
        val startValue = start.toLongOrNull() ?: return false
        val endValue = end.toLongOrNull() ?: return false
        val totalValue = total.toLongOrNull() ?: return false
        return startValue == offset && totalValue == expectedSize && endValue == expectedSize - 1L
    }

    /** The strong ETag, or else Last-Modified. Null when the server gave neither, so the partial cannot be resumed. */
    fun strongValidator(etag: String?, lastModified: String?): String? {
        val tag = etag?.trim()
        if (!tag.isNullOrEmpty() && !tag.startsWith("W/")) return tag
        return lastModified?.trim()?.takeIf { it.isNotEmpty() }
    }
}
