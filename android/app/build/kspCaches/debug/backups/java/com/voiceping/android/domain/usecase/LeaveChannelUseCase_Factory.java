package com.voiceping.android.domain.usecase;

import com.voiceping.android.data.repository.ChannelRepository;
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
public final class LeaveChannelUseCase_Factory implements Factory<LeaveChannelUseCase> {
  private final Provider<ChannelRepository> channelRepositoryProvider;

  private LeaveChannelUseCase_Factory(Provider<ChannelRepository> channelRepositoryProvider) {
    this.channelRepositoryProvider = channelRepositoryProvider;
  }

  @Override
  public LeaveChannelUseCase get() {
    return newInstance(channelRepositoryProvider.get());
  }

  public static LeaveChannelUseCase_Factory create(
      Provider<ChannelRepository> channelRepositoryProvider) {
    return new LeaveChannelUseCase_Factory(channelRepositoryProvider);
  }

  public static LeaveChannelUseCase newInstance(ChannelRepository channelRepository) {
    return new LeaveChannelUseCase(channelRepository);
  }
}
