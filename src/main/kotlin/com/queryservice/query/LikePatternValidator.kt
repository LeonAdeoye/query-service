package com.queryservice.query

import com.queryservice.error.ErrorCodes
import com.queryservice.error.QueryServiceException
import org.springframework.stereotype.Component

/**
 * Rejects SQL that contains a LIKE predicate with two or more `%` wildcards in the pattern.
 * One `%` is allowed (e.g. `LIKE 'a%'` or `LIKE '%a'`); patterns like `LIKE '%text%'` are rejected.
 */
@Component
class LikePatternValidator {

    fun validateNoDoubleLikeWildcard(sql: String) {
        val upper = sql.uppercase()
        var i = 0
        while (i < upper.length) {
            val likeStart = upper.indexOf("LIKE", i, ignoreCase = true)
            if (likeStart == -1) break
            i = likeStart + 4
            // Skip whitespace
            while (i < upper.length && upper[i].isWhitespace()) i++
            if (i >= upper.length) break
            // Optional N prefix for national character string (e.g. N'%x%')
            if (i < upper.length && upper[i] == 'N' && i + 1 < upper.length && (upper[i + 1] == '\'' || upper[i + 1] == '"')) i++
            val quote = upper[i]
            if (quote != '\'' && quote != '"') continue
            i++
            val literalStart = i
            // Parse quoted string (handle escaped quote '' or "")
            while (i < upper.length) {
                when {
                    i + 1 < upper.length && upper[i] == quote && upper[i + 1] == quote -> i += 2
                    upper[i] == quote -> break
                    else -> i++
                }
            }
            if (i >= upper.length) break
            val literalContent = sql.substring(literalStart, i)
            val percentCount = literalContent.count { it == '%' }
            if (percentCount >= 2) {
                throw QueryServiceException(
                    ErrorCodes.LIKE_DOUBLE_WILDCARD_NOT_ALLOWED,
                    "LIKE pattern with two or more % wildcards is not allowed (found $percentCount in pattern)"
                )
            }
            i++
        }
    }
}
