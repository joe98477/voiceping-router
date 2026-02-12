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
public final class AudioRouter_Factory implements Factory<AudioRouter> {
  private final Provider<Context> contextProvider;

  private AudioRouter_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public AudioRouter get() {
    return newInstance(contextProvider.get());
  }

  public static AudioRouter_Factory create(Provider<Context> contextProvider) {
    return new AudioRouter_Factory(contextProvider);
  }

  public static AudioRouter newInstance(Context context) {
    return new AudioRouter(context);
  }
}
