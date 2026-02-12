package com.voiceping.android.presentation;

import com.voiceping.android.data.hardware.HardwareKeyHandler;
import com.voiceping.android.data.ptt.PttManager;
import com.voiceping.android.data.repository.ChannelRepository;
import com.voiceping.android.data.storage.PreferencesManager;
import dagger.MembersInjector;
import dagger.internal.DaggerGenerated;
import dagger.internal.InjectedFieldSignature;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import javax.annotation.processing.Generated;

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
public final class MainActivity_MembersInjector implements MembersInjector<MainActivity> {
  private final Provider<PreferencesManager> preferencesManagerProvider;

  private final Provider<HardwareKeyHandler> hardwareKeyHandlerProvider;

  private final Provider<PttManager> pttManagerProvider;

  private final Provider<ChannelRepository> channelRepositoryProvider;

  private MainActivity_MembersInjector(Provider<PreferencesManager> preferencesManagerProvider,
      Provider<HardwareKeyHandler> hardwareKeyHandlerProvider,
      Provider<PttManager> pttManagerProvider,
      Provider<ChannelRepository> channelRepositoryProvider) {
    this.preferencesManagerProvider = preferencesManagerProvider;
    this.hardwareKeyHandlerProvider = hardwareKeyHandlerProvider;
    this.pttManagerProvider = pttManagerProvider;
    this.channelRepositoryProvider = channelRepositoryProvider;
  }

  @Override
  public void injectMembers(MainActivity instance) {
    injectPreferencesManager(instance, preferencesManagerProvider.get());
    injectHardwareKeyHandler(instance, hardwareKeyHandlerProvider.get());
    injectPttManager(instance, pttManagerProvider.get());
    injectChannelRepository(instance, channelRepositoryProvider.get());
  }

  public static MembersInjector<MainActivity> create(
      Provider<PreferencesManager> preferencesManagerProvider,
      Provider<HardwareKeyHandler> hardwareKeyHandlerProvider,
      Provider<PttManager> pttManagerProvider,
      Provider<ChannelRepository> channelRepositoryProvider) {
    return new MainActivity_MembersInjector(preferencesManagerProvider, hardwareKeyHandlerProvider, pttManagerProvider, channelRepositoryProvider);
  }

  @InjectedFieldSignature("com.voiceping.android.presentation.MainActivity.preferencesManager")
  public static void injectPreferencesManager(MainActivity instance,
      PreferencesManager preferencesManager) {
    instance.preferencesManager = preferencesManager;
  }

  @InjectedFieldSignature("com.voiceping.android.presentation.MainActivity.hardwareKeyHandler")
  public static void injectHardwareKeyHandler(MainActivity instance,
      HardwareKeyHandler hardwareKeyHandler) {
    instance.hardwareKeyHandler = hardwareKeyHandler;
  }

  @InjectedFieldSignature("com.voiceping.android.presentation.MainActivity.pttManager")
  public static void injectPttManager(MainActivity instance, PttManager pttManager) {
    instance.pttManager = pttManager;
  }

  @InjectedFieldSignature("com.voiceping.android.presentation.MainActivity.channelRepository")
  public static void injectChannelRepository(MainActivity instance,
      ChannelRepository channelRepository) {
    instance.channelRepository = channelRepository;
  }
}
