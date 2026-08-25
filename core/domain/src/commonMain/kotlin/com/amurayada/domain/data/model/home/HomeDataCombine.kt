package com.amurayada.domain.data.model.home

import com.amurayada.domain.data.model.home.chart.Chart
import com.amurayada.domain.data.model.mood.Mood
import com.amurayada.domain.utils.Resource

data class HomeDataCombine(
    val home: Resource<Pair<String?, List<HomeItem>>>,
    val mood: Resource<Mood>,
    val chart: Resource<Chart>,
    val newRelease: Resource<List<HomeItem>>,
)