package com.voiceping.android.presentation.events;

import com.voiceping.android.data.storage.PreferencesManager;
import com.voiceping.android.domain.usecase.GetEventsUseCase;
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
public final class EventPickerViewModel_Factory implements Factory<EventPickerViewModel> {
  private final Provider<GetEventsUseCase> getEventsUseCaseProvider;

  private final Provider<PreferencesManager> preferencesManagerProvider;

  private EventPickerViewModel_Factory(Provider<GetEventsUseCase> getEventsUseCaseProvider,
      Provider<PreferencesManager> preferencesManagerProvider) {
    this.getEventsUseCaseProvider = getEventsUseCaseProvider;
    this.preferencesManagerProvider = preferencesManagerProvider;
  }

  @Override
  public EventPickerViewModel get() {
    return newInstance(getEventsUseCaseProvider.get(), preferencesManagerProvider.get());
  }

  public static EventPickerViewModel_Factory create(
      Provider<GetEventsUseCase> getEventsUseCaseProvider,
      Provider<PreferencesManager> preferencesManagerProvider) {
    return new EventPickerViewModel_Factory(getEventsUseCaseProvider, preferencesManagerProvider);
  }

  public static EventPickerViewModel newInstance(GetEventsUseCase getEventsUseCase,
      PreferencesManager preferencesManager) {
    return new EventPickerViewModel(getEventsUseCase, preferencesManager);
  }
}
