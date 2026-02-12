package com.voiceping.android.data.storage;

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
public final class TokenManager_Factory implements Factory<TokenManager> {
  private final Provider<Context> contextProvider;

  private TokenManager_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public TokenManager get() {
    return newInstance(contextProvider.get());
  }

  public static TokenManager_Factory create(Provider<Context> contextProvider) {
    return new TokenManager_Factory(contextProvider);
  }

  public static TokenManager newInstance(Context context) {
    return new TokenManager(context);
  }
}
