package com.voiceping.android.domain.usecase

import com.voiceping.android.data.repository.ChannelRepository
import javax.inject.Inject

class LeaveChannelUseCase @Inject constructor(
    private val channelRepository: ChannelRepository
) {
    suspend operator fun invoke(channelId: String): Result<Unit> {
        return channelRepository.leaveChannel(channelId)
    }
}
