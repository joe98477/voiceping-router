package com.voiceping.android.presentation.login;

import com.voiceping.android.data.storage.TokenManager;
import com.voiceping.android.domain.usecase.LoginUseCase;
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
public final class LoginViewModel_Factory implements Factory<LoginViewModel> {
  private final Provider<LoginUseCase> loginUseCaseProvider;

  private final Provider<TokenManager> tokenManagerProvider;

  private LoginViewModel_Factory(Provider<LoginUseCase> loginUseCaseProvider,
      Provider<TokenManager> tokenManagerProvider) {
    this.loginUseCaseProvider = loginUseCaseProvider;
    this.tokenManagerProvider = tokenManagerProvider;
  }

  @Override
  public LoginViewModel get() {
    return newInstance(loginUseCaseProvider.get(), tokenManagerProvider.get());
  }

  public static LoginViewModel_Factory create(Provider<LoginUseCase> loginUseCaseProvider,
      Provider<TokenManager> tokenManagerProvider) {
    return new LoginViewModel_Factory(loginUseCaseProvider, tokenManagerProvider);
  }

  public static LoginViewModel newInstance(LoginUseCase loginUseCase, TokenManager tokenManager) {
    return new LoginViewModel(loginUseCase, tokenManager);
  }
}
