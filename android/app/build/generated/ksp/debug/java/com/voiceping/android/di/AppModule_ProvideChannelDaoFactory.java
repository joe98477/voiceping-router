package com.voiceping.android.di;

import com.voiceping.android.data.database.VoicePingDatabase;
import com.voiceping.android.data.database.dao.ChannelDao;
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
public final class AppModule_ProvideChannelDaoFactory implements Factory<ChannelDao> {
  private final Provider<VoicePingDatabase> databaseProvider;

  private AppModule_ProvideChannelDaoFactory(Provider<VoicePingDatabase> databaseProvider) {
    this.databaseProvider = databaseProvider;
  }

  @Override
  public ChannelDao get() {
    return provideChannelDao(databaseProvider.get());
  }

  public static AppModule_ProvideChannelDaoFactory create(
      Provider<VoicePingDatabase> databaseProvider) {
    return new AppModule_ProvideChannelDaoFactory(databaseProvider);
  }

  public static ChannelDao provideChannelDao(VoicePingDatabase database) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideChannelDao(database));
  }
}
