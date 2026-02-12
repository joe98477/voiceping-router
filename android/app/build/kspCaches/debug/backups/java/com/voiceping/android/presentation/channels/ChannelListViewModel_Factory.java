package com.voiceping.android.presentation.channels;

import android.content.Context;
import androidx.lifecycle.SavedStateHandle;
import com.voiceping.android.data.audio.AudioRouter;
import com.voiceping.android.data.network.NetworkMonitor;
import com.voiceping.android.data.network.SignalingClient;
import com.voiceping.android.data.ptt.PttManager;
import com.voiceping.android.data.repository.ChannelRepository;
import com.voiceping.android.data.repository.EventRepository;
import com.voiceping.android.data.repository.TransmissionHistoryRepository;
import com.voiceping.android.data.storage.PreferencesManager;
import com.voiceping.android.data.storage.SettingsRepository;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata
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
public final class ChannelListViewModel_Factory implements Factory<ChannelListViewModel> {
  private final Provider<EventRepository> eventRepositoryProvider;

  private final Provider<SignalingClient> signalingClientProvider;

  private final Provider<ChannelRepository> channelRepositoryProvider;

  private final Provider<PreferencesManager> preferencesManagerProvider;

  private final Provider<PttManager> pttManagerProvider;

  private final Provider<SettingsRepository> settingsRepositoryProvider;

  private final Provider<AudioRouter> audioRouterProvider;

  private final Provider<NetworkMonitor> networkMonitorProvider;

  private final Provider<TransmissionHistoryRepository> transmissionHistoryRepositoryProvider;

  private final Provider<Context> contextProvider;

  private final Provider<SavedStateHandle> savedStateHandleProvider;

  private ChannelListViewModel_Factory(Provider<EventRepository> eventRepositoryProvider,
      Provider<SignalingClient> signalingClientProvider,
      Provider<ChannelRepository> channelRepositoryProvider,
      Provider<PreferencesManager> preferencesManagerProvider,
      Provider<PttManager> pttManagerProvider,
      Provider<SettingsRepository> settingsRepositoryProvider,
      Provider<AudioRouter> audioRouterProvider, Provider<NetworkMonitor> networkMonitorProvider,
      Provider<TransmissionHistoryRepository> transmissionHistoryRepositoryProvider,
      Provider<Context> contextProvider, Provider<SavedStateHandle> savedStateHandleProvider) {
    this.eventRepositoryProvider = eventRepositoryProvider;
    this.signalingClientProvider = signalingClientProvider;
    this.channelRepositoryProvider = channelRepositoryProvider;
    this.preferencesManagerProvider = preferencesManagerProvider;
    this.pttManagerProvider = pttManagerProvider;
    this.settingsRepositoryProvider = settingsRepositoryProvider;
    this.audioRouterProvider = audioRouterProvider;
    this.networkMonitorProvider = networkMonitorProvider;
    this.transmissionHistoryRepositoryProvider = transmissionHistoryRepositoryProvider;
    this.contextProvider = contextProvider;
    this.savedStateHandleProvider = savedStateHandleProvider;
  }

  @Override
  public ChannelListViewModel get() {
    return newInstance(eventRepositoryProvider.get(), signalingClientProvider.get(), channelRepositoryProvider.get(), preferencesManagerProvider.get(), pttManagerProvider.get(), settingsRepositoryProvider.get(), audioRouterProvider.get(), networkMonitorProvider.get(), transmissionHistoryRepositoryProvider.get(), contextProvider.get(), savedStateHandleProvider.get());
  }

  public static ChannelListViewModel_Factory create(
      Provider<EventRepository> eventRepositoryProvider,
      Provider<SignalingClient> signalingClientProvider,
      Provider<ChannelRepository> channelRepositoryProvider,
      Provider<PreferencesManager> preferencesManagerProvider,
      Provider<PttManager> pttManagerProvider,
      Provider<SettingsRepository> settingsRepositoryProvider,
      Provider<AudioRouter> audioRouterProvider, Provider<NetworkMonitor> networkMonitorProvider,
      Provider<TransmissionHistoryRepository> transmissionHistoryRepositoryProvider,
      Provider<Context> contextProvider, Provider<SavedStateHandle> savedStateHandleProvider) {
    return new ChannelListViewModel_Factory(eventRepositoryProvider, signalingClientProvider, channelRepositoryProvider, preferencesManagerProvider, pttManagerProvider, settingsRepositoryProvider, audioRouterProvider, networkMonitorProvider, transmissionHistoryRepositoryProvider, contextProvider, savedStateHandleProvider);
  }

  public static ChannelListViewModel newInstance(EventRepository eventRepository,
      SignalingClient signalingClient, ChannelRepository channelRepository,
      PreferencesManager preferencesManager, PttManager pttManager,
      SettingsRepository settingsRepository, AudioRouter audioRouter, NetworkMonitor networkMonitor,
      TransmissionHistoryRepository transmissionHistoryRepository, Context context,
      SavedStateHandle savedStateHandle) {
    return new ChannelListViewModel(eventRepository, signalingClient, channelRepository, preferencesManager, pttManager, settingsRepository, audioRouter, networkMonitor, transmissionHistoryRepository, context, savedStateHandle);
  }
}
