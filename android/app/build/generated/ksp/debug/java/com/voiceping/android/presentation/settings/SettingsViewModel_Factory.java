package com.voiceping.android.presentation.settings;

import com.voiceping.android.data.audio.AudioRouter;
import com.voiceping.android.data.storage.SettingsRepository;
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
public final class SettingsViewModel_Factory implements Factory<SettingsViewModel> {
  private final Provider<SettingsRepository> settingsRepositoryProvider;

  private final Provider<AudioRouter> audioRouterProvider;

  private SettingsViewModel_Factory(Provider<SettingsRepository> settingsRepositoryProvider,
      Provider<AudioRouter> audioRouterProvider) {
    this.settingsRepositoryProvider = settingsRepositoryProvider;
    this.audioRouterProvider = audioRouterProvider;
  }

  @Override
  public SettingsViewModel get() {
    return newInstance(settingsRepositoryProvider.get(), audioRouterProvider.get());
  }

  public static SettingsViewModel_Factory create(
      Provider<SettingsRepository> settingsRepositoryProvider,
      Provider<AudioRouter> audioRouterProvider) {
    return new SettingsViewModel_Factory(settingsRepositoryProvider, audioRouterProvider);
  }

  public static SettingsViewModel newInstance(SettingsRepository settingsRepository,
      AudioRouter audioRouter) {
    return new SettingsViewModel(settingsRepository, audioRouter);
  }
}
