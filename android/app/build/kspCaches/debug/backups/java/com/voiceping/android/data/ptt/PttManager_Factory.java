package com.voiceping.android.data.ptt;

import android.content.Context;
import com.voiceping.android.data.audio.AudioCaptureManager;
import com.voiceping.android.data.audio.AudioRouter;
import com.voiceping.android.data.network.MediasoupClient;
import com.voiceping.android.data.network.SignalingClient;
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
public final class PttManager_Factory implements Factory<PttManager> {
  private final Provider<SignalingClient> signalingClientProvider;

  private final Provider<MediasoupClient> mediasoupClientProvider;

  private final Provider<AudioCaptureManager> audioCaptureManagerProvider;

  private final Provider<AudioRouter> audioRouterProvider;

  private final Provider<Context> contextProvider;

  private PttManager_Factory(Provider<SignalingClient> signalingClientProvider,
      Provider<MediasoupClient> mediasoupClientProvider,
      Provider<AudioCaptureManager> audioCaptureManagerProvider,
      Provider<AudioRouter> audioRouterProvider, Provider<Context> contextProvider) {
    this.signalingClientProvider = signalingClientProvider;
    this.mediasoupClientProvider = mediasoupClientProvider;
    this.audioCaptureManagerProvider = audioCaptureManagerProvider;
    this.audioRouterProvider = audioRouterProvider;
    this.contextProvider = contextProvider;
  }

  @Override
  public PttManager get() {
    return newInstance(signalingClientProvider.get(), mediasoupClientProvider.get(), audioCaptureManagerProvider.get(), audioRouterProvider.get(), contextProvider.get());
  }

  public static PttManager_Factory create(Provider<SignalingClient> signalingClientProvider,
      Provider<MediasoupClient> mediasoupClientProvider,
      Provider<AudioCaptureManager> audioCaptureManagerProvider,
      Provider<AudioRouter> audioRouterProvider, Provider<Context> contextProvider) {
    return new PttManager_Factory(signalingClientProvider, mediasoupClientProvider, audioCaptureManagerProvider, audioRouterProvider, contextProvider);
  }

  public static PttManager newInstance(SignalingClient signalingClient,
      MediasoupClient mediasoupClient, AudioCaptureManager audioCaptureManager,
      AudioRouter audioRouter, Context context) {
    return new PttManager(signalingClient, mediasoupClient, audioCaptureManager, audioRouter, context);
  }
}
