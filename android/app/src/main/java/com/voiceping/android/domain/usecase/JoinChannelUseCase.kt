package com.voiceping.android.domain.usecase

import com.voiceping.android.data.repository.ChannelRepository
import javax.inject.Inject

class JoinChannelUseCase @Inject constructor(
    private val channelRepository: ChannelRepository
) {
    suspend operator fun invoke(channelId: String, channelName: String = "", teamName: String = ""): Result<Unit> {
        return channelRepository.joinChannel(channelId, channelName, teamName)
    }
}
