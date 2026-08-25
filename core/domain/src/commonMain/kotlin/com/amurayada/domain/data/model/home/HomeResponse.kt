package com.amurayada.domain.data.model.home

import com.amurayada.domain.data.model.home.chart.Chart
import com.amurayada.domain.data.model.mood.Mood
import com.amurayada.domain.utils.Resource

data class HomeResponse(
    val homeItem: Resource<ArrayList<HomeItem>>,
    val exploreMood: Resource<Mood>,
    val exploreChart: Resource<Chart>,
)