package com.voiceping.android.data.audio;

import com.voiceping.android.data.storage.SettingsRepository;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata("javax.inject.Singleton")
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
public final class TonePlayer_Factory implements Factory<TonePlayer> {
  private final Provider<SettingsRepository> settingsRepositoryProvider;

  private TonePlayer_Factory(Provider<SettingsRepository> settingsRepositoryProvider) {
    this.settingsRepositoryProvider = settingsRepositoryProvider;
  }

  @Override
  public TonePlayer get() {
    return newInstance(settingsRepositoryProvider.get());
  }

  public static TonePlayer_Factory create(Provider<SettingsRepository> settingsRepositoryProvider) {
    return new TonePlayer_Factory(settingsRepositoryProvider);
  }

  public static TonePlayer newInstance(SettingsRepository settingsRepository) {
    return new TonePlayer(settingsRepository);
  }
}
