package com.voiceping.android.di;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import okhttp3.OkHttpClient;

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
public final class AppModule_ProvideOkHttpClientFactory implements Factory<OkHttpClient> {
  private final Provider<SessionCookieJar> cookieJarProvider;

  private AppModule_ProvideOkHttpClientFactory(Provider<SessionCookieJar> cookieJarProvider) {
    this.cookieJarProvider = cookieJarProvider;
  }

  @Override
  public OkHttpClient get() {
    return provideOkHttpClient(cookieJarProvider.get());
  }

  public static AppModule_ProvideOkHttpClientFactory create(
      Provider<SessionCookieJar> cookieJarProvider) {
    return new AppModule_ProvideOkHttpClientFactory(cookieJarProvider);
  }

  public static OkHttpClient provideOkHttpClient(SessionCookieJar cookieJar) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideOkHttpClient(cookieJar));
  }
}
