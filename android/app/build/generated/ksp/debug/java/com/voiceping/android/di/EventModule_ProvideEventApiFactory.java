package com.voiceping.android.di;

import com.voiceping.android.data.api.EventApi;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
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
public final class EventModule_ProvideEventApiFactory implements Factory<EventApi> {
  private final Provider<Retrofit> retrofitProvider;

  private EventModule_ProvideEventApiFactory(Provider<Retrofit> retrofitProvider) {
    this.retrofitProvider = retrofitProvider;
  }

  @Override
  public EventApi get() {
    return provideEventApi(retrofitProvider.get());
  }

  public static EventModule_ProvideEventApiFactory create(Provider<Retrofit> retrofitProvider) {
    return new EventModule_ProvideEventApiFactory(retrofitProvider);
  }

  public static EventApi provideEventApi(Retrofit retrofit) {
    return Preconditions.checkNotNullFromProvides(EventModule.INSTANCE.provideEventApi(retrofit));
  }
}
