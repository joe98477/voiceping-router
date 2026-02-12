package com.voiceping.android.data.repository;

import android.content.Context;
import com.voiceping.android.data.audio.AudioDeviceManager;
import com.voiceping.android.data.audio.AudioRouter;
import com.voiceping.android.data.audio.HapticFeedback;
import com.voiceping.android.data.audio.TonePlayer;
import com.voiceping.android.data.hardware.MediaButtonHandler;
import com.voiceping.android.data.network.MediasoupClient;
import com.voiceping.android.data.network.NetworkMonitor;
import com.voiceping.android.data.network.SignalingClient;
import com.voiceping.android.data.ptt.PttManager;
import com.voiceping.android.data.storage.SettingsRepository;
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
public final class ChannelRepository_Factory implements Factory<ChannelRepository> {
  private final Provider<SignalingClient> signalingClientProvider;

  private final Provider<MediasoupClient> mediasoupClientProvider;

  private final Provider<AudioRouter> audioRouterProvider;

  private final Provider<PttManager> pttManagerProvider;

  private final Provider<TonePlayer> tonePlayerProvider;

  private final Provider<HapticFeedback> hapticFeedbackProvider;

  private final Provider<SettingsRepository> settingsRepositoryProvider;

  private final Provider<AudioDeviceManager> audioDeviceManagerProvider;

  private final Provider<MediaButtonHandler> mediaButtonHandlerProvider;

  private final Provider<NetworkMonitor> networkMonitorProvider;

  private final Provider<Context> contextProvider;

  private ChannelRepository_Factory(Provider<SignalingClient> signalingClientProvider,
      Provider<MediasoupClient> mediasoupClientProvider, Provider<AudioRouter> audioRouterProvider,
      Provider<PttManager> pttManagerProvider, Provider<TonePlayer> tonePlayerProvider,
      Provider<HapticFeedback> hapticFeedbackProvider,
      Provider<SettingsRepository> settingsRepositoryProvider,
      Provider<AudioDeviceManager> audioDeviceManagerProvider,
      Provider<MediaButtonHandler> mediaButtonHandlerProvider,
      Provider<NetworkMonitor> networkMonitorProvider, Provider<Context> contextProvider) {
    this.signalingClientProvider = signalingClientProvider;
    this.mediasoupClientProvider = mediasoupClientProvider;
    this.audioRouterProvider = audioRouterProvider;
    this.pttManagerProvider = pttManagerProvider;
    this.tonePlayerProvider = tonePlayerProvider;
    this.hapticFeedbackProvider = hapticFeedbackProvider;
    this.settingsRepositoryProvider = settingsRepositoryProvider;
    this.audioDeviceManagerProvider = audioDeviceManagerProvider;
    this.mediaButtonHandlerProvider = mediaButtonHandlerProvider;
    this.networkMonitorProvider = networkMonitorProvider;
    this.contextProvider = contextProvider;
  }

  @Override
  public ChannelRepository get() {
    return newInstance(signalingClientProvider.get(), mediasoupClientProvider.get(), audioRouterProvider.get(), pttManagerProvider.get(), tonePlayerProvider.get(), hapticFeedbackProvider.get(), settingsRepositoryProvider.get(), audioDeviceManagerProvider.get(), mediaButtonHandlerProvider.get(), networkMonitorProvider.get(), contextProvider.get());
  }

  public static ChannelRepository_Factory create(Provider<SignalingClient> signalingClientProvider,
      Provider<MediasoupClient> mediasoupClientProvider, Provider<AudioRouter> audioRouterProvider,
      Provider<PttManager> pttManagerProvider, Provider<TonePlayer> tonePlayerProvider,
      Provider<HapticFeedback> hapticFeedbackProvider,
      Provider<SettingsRepository> settingsRepositoryProvider,
      Provider<AudioDeviceManager> audioDeviceManagerProvider,
      Provider<MediaButtonHandler> mediaButtonHandlerProvider,
      Provider<NetworkMonitor> networkMonitorProvider, Provider<Context> contextProvider) {
    return new ChannelRepository_Factory(signalingClientProvider, mediasoupClientProvider, audioRouterProvider, pttManagerProvider, tonePlayerProvider, hapticFeedbackProvider, settingsRepositoryProvider, audioDeviceManagerProvider, mediaButtonHandlerProvider, networkMonitorProvider, contextProvider);
  }

  public static ChannelRepository newInstance(SignalingClient signalingClient,
      MediasoupClient mediasoupClient, AudioRouter audioRouter, PttManager pttManager,
      TonePlayer tonePlayer, HapticFeedback hapticFeedback, SettingsRepository settingsRepository,
      AudioDeviceManager audioDeviceManager, MediaButtonHandler mediaButtonHandler,
      NetworkMonitor networkMonitor, Context context) {
    return new ChannelRepository(signalingClient, mediasoupClient, audioRouter, pttManager, tonePlayer, hapticFeedback, settingsRepository, audioDeviceManager, mediaButtonHandler, networkMonitor, context);
  }
}
