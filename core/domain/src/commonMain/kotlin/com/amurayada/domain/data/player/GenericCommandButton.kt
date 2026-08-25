package com.amurayada.domain.data.player

import com.amurayada.domain.mediaservice.handler.RepeatState

sealed class GenericCommandButton {
    data class Like(
        val isLiked: Boolean,
    ) : GenericCommandButton()

    data class Shuffle(
        val isShuffled: Boolean,
    ) : GenericCommandButton()

    data class Repeat(
        val repeatState: RepeatState,
    ) : GenericCommandButton()

    data object Radio : GenericCommandButton()
}