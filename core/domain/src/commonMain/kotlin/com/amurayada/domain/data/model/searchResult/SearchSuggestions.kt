package com.amurayada.domain.data.model.searchResult

import com.amurayada.domain.data.type.SearchResultType

data class SearchSuggestions(
    val queries: List<String>,
    val recommendedItems: List<SearchResultType>,
)