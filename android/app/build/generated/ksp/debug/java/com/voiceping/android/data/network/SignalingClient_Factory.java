package com.voiceping.android.data.network;

import com.google.gson.Gson;
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
public final class SignalingClient_Factory implements Factory<SignalingClient> {
  private final Provider<Gson> gsonProvider;

  private final Provider<NetworkMonitor> networkMonitorProvider;

  private SignalingClient_Factory(Provider<Gson> gsonProvider,
      Provider<NetworkMonitor> networkMonitorProvider) {
    this.gsonProvider = gsonProvider;
    this.networkMonitorProvider = networkMonitorProvider;
  }

  @Override
  public SignalingClient get() {
    return newInstance(gsonProvider.get(), networkMonitorProvider.get());
  }

  public static SignalingClient_Factory create(Provider<Gson> gsonProvider,
      Provider<NetworkMonitor> networkMonitorProvider) {
    return new SignalingClient_Factory(gsonProvider, networkMonitorProvider);
  }

  public static SignalingClient newInstance(Gson gson, NetworkMonitor networkMonitor) {
    return new SignalingClient(gson, networkMonitor);
  }
}
