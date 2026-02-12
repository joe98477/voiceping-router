package com.voiceping.android.domain.usecase

import com.voiceping.android.data.repository.EventRepository
import com.voiceping.android.domain.model.Event
import javax.inject.Inject

class GetEventsUseCase @Inject constructor(
    private val eventRepository: EventRepository
) {
    suspend operator fun invoke(): Result<List<Event>> {
        return eventRepository.getEventsWithCache()
    }
}
