package com.voiceping.android.domain.usecase;

import com.voiceping.android.data.repository.EventRepository;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata
@QualifierMetadata
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast",
    "deprecation",
    "nullness:initialization.field.uninitialized"
})
public final class GetEventsUseCase_Factory implements Factory<GetEventsUseCase> {
  private final Provider<EventRepository> eventRepositoryProvider;

  private GetEventsUseCase_Factory(Provider<EventRepository> eventRepositoryProvider) {
    this.eventRepositoryProvider = eventRepositoryProvider;
  }

  @Override
  public GetEventsUseCase get() {
    return newInstance(eventRepositoryProvider.get());
  }

  public static GetEventsUseCase_Factory create(Provider<EventRepository> eventRepositoryProvider) {
    return new GetEventsUseCase_Factory(eventRepositoryProvider);
  }

  public static GetEventsUseCase newInstance(EventRepository eventRepository) {
    return new GetEventsUseCase(eventRepository);
  }
}
