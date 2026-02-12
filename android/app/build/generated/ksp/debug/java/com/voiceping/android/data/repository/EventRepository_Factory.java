package com.voiceping.android.data.repository;

import com.voiceping.android.data.api.EventApi;
import com.voiceping.android.data.database.dao.ChannelDao;
import com.voiceping.android.data.database.dao.EventDao;
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
public final class EventRepository_Factory implements Factory<EventRepository> {
  private final Provider<EventApi> eventApiProvider;

  private final Provider<EventDao> eventDaoProvider;

  private final Provider<ChannelDao> channelDaoProvider;

  private EventRepository_Factory(Provider<EventApi> eventApiProvider,
      Provider<EventDao> eventDaoProvider, Provider<ChannelDao> channelDaoProvider) {
    this.eventApiProvider = eventApiProvider;
    this.eventDaoProvider = eventDaoProvider;
    this.channelDaoProvider = channelDaoProvider;
  }

  @Override
  public EventRepository get() {
    return newInstance(eventApiProvider.get(), eventDaoProvider.get(), channelDaoProvider.get());
  }

  public static EventRepository_Factory create(Provider<EventApi> eventApiProvider,
      Provider<EventDao> eventDaoProvider, Provider<ChannelDao> channelDaoProvider) {
    return new EventRepository_Factory(eventApiProvider, eventDaoProvider, channelDaoProvider);
  }

  public static EventRepository newInstance(EventApi eventApi, EventDao eventDao,
      ChannelDao channelDao) {
    return new EventRepository(eventApi, eventDao, channelDao);
  }
}
