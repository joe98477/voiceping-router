package com.voiceping.android.data.hardware;

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
public final class HardwareKeyHandler_Factory implements Factory<HardwareKeyHandler> {
  private final Provider<SettingsRepository> settingsRepositoryProvider;

  private HardwareKeyHandler_Factory(Provider<SettingsRepository> settingsRepositoryProvider) {
    this.settingsRepositoryProvider = settingsRepositoryProvider;
  }

  @Override
  public HardwareKeyHandler get() {
    return newInstance(settingsRepositoryProvider.get());
  }

  public static HardwareKeyHandler_Factory create(
      Provider<SettingsRepository> settingsRepositoryProvider) {
    return new HardwareKeyHandler_Factory(settingsRepositoryProvider);
  }

  public static HardwareKeyHandler newInstance(SettingsRepository settingsRepository) {
    return new HardwareKeyHandler(settingsRepository);
  }
}
