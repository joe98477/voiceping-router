package com.voiceping.android.di;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
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
public final class AppModule_ProvideSessionCookieJarFactory implements Factory<SessionCookieJar> {
  @Override
  public SessionCookieJar get() {
    return provideSessionCookieJar();
  }

  public static AppModule_ProvideSessionCookieJarFactory create() {
    return InstanceHolder.INSTANCE;
  }

  public static SessionCookieJar provideSessionCookieJar() {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideSessionCookieJar());
  }

  private static final class InstanceHolder {
    static final AppModule_ProvideSessionCookieJarFactory INSTANCE = new AppModule_ProvideSessionCookieJarFactory();
  }
}
