package com.amurayada.kotlinytmusicscraper.models.body

import com.amurayada.kotlinytmusicscraper.models.Context
import kotlinx.serialization.Serializable

@Serializable
data class SearchBody(
    val context: Context,
    val query: String?,
    val params: String?,
)