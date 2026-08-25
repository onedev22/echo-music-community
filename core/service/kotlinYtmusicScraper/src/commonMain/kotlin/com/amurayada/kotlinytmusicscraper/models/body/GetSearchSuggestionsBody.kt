package com.amurayada.kotlinytmusicscraper.models.body

import com.amurayada.kotlinytmusicscraper.models.Context
import kotlinx.serialization.Serializable

@Serializable
data class GetSearchSuggestionsBody(
    val context: Context,
    val input: String,
)