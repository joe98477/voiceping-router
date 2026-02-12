package com.voiceping.android.data.audio;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
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
public final class AudioCaptureManager_Factory implements Factory<AudioCaptureManager> {
  @Override
  public AudioCaptureManager get() {
    return newInstance();
  }

  public static AudioCaptureManager_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static AudioCaptureManager newInstance() {
    return new AudioCaptureManager();
  }

  private static final class InstanceHolder {
    static final AudioCaptureManager_Factory INSTANCE = new AudioCaptureManager_Factory();
  }
}
