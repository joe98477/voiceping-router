package com.voiceping.android.presentation.channels;

import androidx.lifecycle.ViewModel;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.components.ActivityRetainedComponent;
import dagger.hilt.android.components.ViewModelComponent;
import dagger.hilt.android.internal.lifecycle.HiltViewModelMap;
import dagger.hilt.codegen.OriginatingElement;
import dagger.multibindings.IntoMap;
import dagger.multibindings.LazyClassKey;
import javax.annotation.processing.Generated;

@Generated("dagger.hilt.android.processor.internal.viewmodel.ViewModelProcessor")
@OriginatingElement(
    topLevelClass = ChannelListViewModel.class
)
public final class ChannelListViewModel_HiltModules {
  private ChannelListViewModel_HiltModules() {
  }

  @Module
  @InstallIn(ViewModelComponent.class)
  public abstract static class BindsModule {
    private BindsModule() {
    }

    @Binds
    @IntoMap
    @LazyClassKey(ChannelListViewModel.class)
    @HiltViewModelMap
    public abstract ViewModel binds(ChannelListViewModel vm);
  }

  @Module
  @InstallIn(ActivityRetainedComponent.class)
  public static final class KeyModule {
    private KeyModule() {
    }

    @Provides
    @IntoMap
    @LazyClassKey(ChannelListViewModel.class)
    @HiltViewModelMap.KeySet
    public static boolean provide() {
      return true;
    }
  }
}
