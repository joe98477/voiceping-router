package com.voiceping.android.data.repository;

import com.voiceping.android.data.api.AuthApi;
import com.voiceping.android.data.storage.PreferencesManager;
import com.voiceping.android.data.storage.TokenManager;
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
public final class AuthRepository_Factory implements Factory<AuthRepository> {
  private final Provider<AuthApi> authApiProvider;

  private final Provider<TokenManager> tokenManagerProvider;

  private final Provider<PreferencesManager> preferencesManagerProvider;

  private AuthRepository_Factory(Provider<AuthApi> authApiProvider,
      Provider<TokenManager> tokenManagerProvider,
      Provider<PreferencesManager> preferencesManagerProvider) {
    this.authApiProvider = authApiProvider;
    this.tokenManagerProvider = tokenManagerProvider;
    this.preferencesManagerProvider = preferencesManagerProvider;
  }

  @Override
  public AuthRepository get() {
    return newInstance(authApiProvider.get(), tokenManagerProvider.get(), preferencesManagerProvider.get());
  }

  public static AuthRepository_Factory create(Provider<AuthApi> authApiProvider,
      Provider<TokenManager> tokenManagerProvider,
      Provider<PreferencesManager> preferencesManagerProvider) {
    return new AuthRepository_Factory(authApiProvider, tokenManagerProvider, preferencesManagerProvider);
  }

  public static AuthRepository newInstance(AuthApi authApi, TokenManager tokenManager,
      PreferencesManager preferencesManager) {
    return new AuthRepository(authApi, tokenManager, preferencesManager);
  }
}
