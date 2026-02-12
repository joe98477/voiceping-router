package com.voiceping.android.data.audio;

import android.content.Context;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata("dagger.hilt.android.qualifiers.ApplicationContext")
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
public final class AudioDeviceManager_Factory implements Factory<AudioDeviceManager> {
  private final Provider<AudioRouter> audioRouterProvider;

  private final Provider<Context> contextProvider;

  private AudioDeviceManager_Factory(Provider<AudioRouter> audioRouterProvider,
      Provider<Context> contextProvider) {
    this.audioRouterProvider = audioRouterProvider;
    this.contextProvider = contextProvider;
  }

  @Override
  public AudioDeviceManager get() {
    return newInstance(audioRouterProvider.get(), contextProvider.get());
  }

  public static AudioDeviceManager_Factory create(Provider<AudioRouter> audioRouterProvider,
      Provider<Context> contextProvider) {
    return new AudioDeviceManager_Factory(audioRouterProvider, contextProvider);
  }

  public static AudioDeviceManager newInstance(AudioRouter audioRouter, Context context) {
    return new AudioDeviceManager(audioRouter, context);
  }
}
