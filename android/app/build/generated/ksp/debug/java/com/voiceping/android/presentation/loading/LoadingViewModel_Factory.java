package com.voiceping.android.presentation.loading;

import com.voiceping.android.data.network.MediasoupClient;
import com.voiceping.android.data.network.SignalingClient;
import com.voiceping.android.data.repository.AuthRepository;
import com.voiceping.android.data.storage.PreferencesManager;
import com.voiceping.android.data.storage.TokenManager;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata
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
public final class LoadingViewModel_Factory implements Factory<LoadingViewModel> {
  private final Provider<SignalingClient> signalingClientProvider;

  private final Provider<MediasoupClient> mediasoupClientProvider;

  private final Provider<PreferencesManager> preferencesManagerProvider;

  private final Provider<TokenManager> tokenManagerProvider;

  private final Provider<AuthRepository> authRepositoryProvider;

  private LoadingViewModel_Factory(Provider<SignalingClient> signalingClientProvider,
      Provider<MediasoupClient> mediasoupClientProvider,
      Provider<PreferencesManager> preferencesManagerProvider,
      Provider<TokenManager> tokenManagerProvider,
      Provider<AuthRepository> authRepositoryProvider) {
    this.signalingClientProvider = signalingClientProvider;
    this.mediasoupClientProvider = mediasoupClientProvider;
    this.preferencesManagerProvider = preferencesManagerProvider;
    this.tokenManagerProvider = tokenManagerProvider;
    this.authRepositoryProvider = authRepositoryProvider;
  }

  @Override
  public LoadingViewModel get() {
    return newInstance(signalingClientProvider.get(), mediasoupClientProvider.get(), preferencesManagerProvider.get(), tokenManagerProvider.get(), authRepositoryProvider.get());
  }

  public static LoadingViewModel_Factory create(Provider<SignalingClient> signalingClientProvider,
      Provider<MediasoupClient> mediasoupClientProvider,
      Provider<PreferencesManager> preferencesManagerProvider,
      Provider<TokenManager> tokenManagerProvider,
      Provider<AuthRepository> authRepositoryProvider) {
    return new LoadingViewModel_Factory(signalingClientProvider, mediasoupClientProvider, preferencesManagerProvider, tokenManagerProvider, authRepositoryProvider);
  }

  public static LoadingViewModel newInstance(SignalingClient signalingClient,
      MediasoupClient mediasoupClient, PreferencesManager preferencesManager,
      TokenManager tokenManager, AuthRepository authRepository) {
    return new LoadingViewModel(signalingClient, mediasoupClient, preferencesManager, tokenManager, authRepository);
  }
}
