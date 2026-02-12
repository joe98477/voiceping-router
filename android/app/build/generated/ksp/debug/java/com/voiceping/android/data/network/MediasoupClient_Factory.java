package com.voiceping.android.data.network;

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
public final class MediasoupClient_Factory implements Factory<MediasoupClient> {
  private final Provider<SignalingClient> signalingClientProvider;

  private final Provider<Context> contextProvider;

  private MediasoupClient_Factory(Provider<SignalingClient> signalingClientProvider,
      Provider<Context> contextProvider) {
    this.signalingClientProvider = signalingClientProvider;
    this.contextProvider = contextProvider;
  }

  @Override
  public MediasoupClient get() {
    return newInstance(signalingClientProvider.get(), contextProvider.get());
  }

  public static MediasoupClient_Factory create(Provider<SignalingClient> signalingClientProvider,
      Provider<Context> contextProvider) {
    return new MediasoupClient_Factory(signalingClientProvider, contextProvider);
  }

  public static MediasoupClient newInstance(SignalingClient signalingClient, Context context) {
    return new MediasoupClient(signalingClient, context);
  }
}
