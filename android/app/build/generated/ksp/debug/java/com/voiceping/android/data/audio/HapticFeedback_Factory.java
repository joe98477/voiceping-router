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
public final class HapticFeedback_Factory implements Factory<HapticFeedback> {
  private final Provider<Context> contextProvider;

  private HapticFeedback_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public HapticFeedback get() {
    return newInstance(contextProvider.get());
  }

  public static HapticFeedback_Factory create(Provider<Context> contextProvider) {
    return new HapticFeedback_Factory(contextProvider);
  }

  public static HapticFeedback newInstance(Context context) {
    return new HapticFeedback(context);
  }
}
