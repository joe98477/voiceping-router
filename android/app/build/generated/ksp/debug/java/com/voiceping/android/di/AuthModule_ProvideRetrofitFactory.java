package com.voiceping.android.di;

import com.google.gson.Gson;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;

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
public final class AuthModule_ProvideRetrofitFactory implements Factory<Retrofit> {
  private final Provider<OkHttpClient> okHttpClientProvider;

  private final Provider<Gson> gsonProvider;

  private AuthModule_ProvideRetrofitFactory(Provider<OkHttpClient> okHttpClientProvider,
      Provider<Gson> gsonProvider) {
    this.okHttpClientProvider = okHttpClientProvider;
    this.gsonProvider = gsonProvider;
  }

  @Override
  public Retrofit get() {
    return provideRetrofit(okHttpClientProvider.get(), gsonProvider.get());
  }

  public static AuthModule_ProvideRetrofitFactory create(
      Provider<OkHttpClient> okHttpClientProvider, Provider<Gson> gsonProvider) {
    return new AuthModule_ProvideRetrofitFactory(okHttpClientProvider, gsonProvider);
  }

  public static Retrofit provideRetrofit(OkHttpClient okHttpClient, Gson gson) {
    return Preconditions.checkNotNullFromProvides(AuthModule.INSTANCE.provideRetrofit(okHttpClient, gson));
  }
}
