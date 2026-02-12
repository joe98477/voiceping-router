package com.voiceping.android.di;

import com.voiceping.android.data.database.VoicePingDatabase;
import com.voiceping.android.data.database.dao.EventDao;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
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
public final class AppModule_ProvideEventDaoFactory implements Factory<EventDao> {
  private final Provider<VoicePingDatabase> databaseProvider;

  private AppModule_ProvideEventDaoFactory(Provider<VoicePingDatabase> databaseProvider) {
    this.databaseProvider = databaseProvider;
  }

  @Override
  public EventDao get() {
    return provideEventDao(databaseProvider.get());
  }

  public static AppModule_ProvideEventDaoFactory create(
      Provider<VoicePingDatabase> databaseProvider) {
    return new AppModule_ProvideEventDaoFactory(databaseProvider);
  }

  public static EventDao provideEventDao(VoicePingDatabase database) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideEventDao(database));
  }
}
