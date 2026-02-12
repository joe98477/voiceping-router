package com.voiceping.android;

import android.app.Activity;
import android.app.Service;
import android.view.View;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.gson.Gson;
import com.voiceping.android.data.api.AuthApi;
import com.voiceping.android.data.api.EventApi;
import com.voiceping.android.data.audio.AudioCaptureManager;
import com.voiceping.android.data.audio.AudioDeviceManager;
import com.voiceping.android.data.audio.AudioRouter;
import com.voiceping.android.data.audio.HapticFeedback;
import com.voiceping.android.data.audio.TonePlayer;
import com.voiceping.android.data.database.VoicePingDatabase;
import com.voiceping.android.data.database.dao.ChannelDao;
import com.voiceping.android.data.database.dao.EventDao;
import com.voiceping.android.data.hardware.HardwareKeyHandler;
import com.voiceping.android.data.hardware.MediaButtonHandler;
import com.voiceping.android.data.network.MediasoupClient;
import com.voiceping.android.data.network.NetworkMonitor;
import com.voiceping.android.data.network.SignalingClient;
import com.voiceping.android.data.ptt.PttManager;
import com.voiceping.android.data.repository.AuthRepository;
import com.voiceping.android.data.repository.ChannelRepository;
import com.voiceping.android.data.repository.EventRepository;
import com.voiceping.android.data.repository.TransmissionHistoryRepository;
import com.voiceping.android.data.storage.PreferencesManager;
import com.voiceping.android.data.storage.SettingsRepository;
import com.voiceping.android.data.storage.TokenManager;
import com.voiceping.android.di.AppModule_ProvideChannelDaoFactory;
import com.voiceping.android.di.AppModule_ProvideDatabaseFactory;
import com.voiceping.android.di.AppModule_ProvideEventDaoFactory;
import com.voiceping.android.di.AppModule_ProvideGsonFactory;
import com.voiceping.android.di.AppModule_ProvideOkHttpClientFactory;
import com.voiceping.android.di.AppModule_ProvideSessionCookieJarFactory;
import com.voiceping.android.di.AuthModule_ProvideAuthApiFactory;
import com.voiceping.android.di.AuthModule_ProvideRetrofitFactory;
import com.voiceping.android.di.EventModule_ProvideEventApiFactory;
import com.voiceping.android.di.SessionCookieJar;
import com.voiceping.android.domain.usecase.GetEventsUseCase;
import com.voiceping.android.domain.usecase.LoginUseCase;
import com.voiceping.android.presentation.MainActivity;
import com.voiceping.android.presentation.MainActivity_MembersInjector;
import com.voiceping.android.presentation.channels.ChannelListViewModel;
import com.voiceping.android.presentation.channels.ChannelListViewModel_HiltModules;
import com.voiceping.android.presentation.channels.ChannelListViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import com.voiceping.android.presentation.channels.ChannelListViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import com.voiceping.android.presentation.events.EventPickerViewModel;
import com.voiceping.android.presentation.events.EventPickerViewModel_HiltModules;
import com.voiceping.android.presentation.events.EventPickerViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import com.voiceping.android.presentation.events.EventPickerViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import com.voiceping.android.presentation.loading.LoadingViewModel;
import com.voiceping.android.presentation.loading.LoadingViewModel_HiltModules;
import com.voiceping.android.presentation.loading.LoadingViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import com.voiceping.android.presentation.loading.LoadingViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import com.voiceping.android.presentation.login.LoginViewModel;
import com.voiceping.android.presentation.login.LoginViewModel_HiltModules;
import com.voiceping.android.presentation.login.LoginViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import com.voiceping.android.presentation.login.LoginViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import com.voiceping.android.presentation.settings.SettingsViewModel;
import com.voiceping.android.presentation.settings.SettingsViewModel_HiltModules;
import com.voiceping.android.presentation.settings.SettingsViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import com.voiceping.android.presentation.settings.SettingsViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import com.voiceping.android.service.AudioCaptureService;
import com.voiceping.android.service.ChannelMonitoringService;
import dagger.hilt.android.ActivityRetainedLifecycle;
import dagger.hilt.android.ViewModelLifecycle;
import dagger.hilt.android.internal.builders.ActivityComponentBuilder;
import dagger.hilt.android.internal.builders.ActivityRetainedComponentBuilder;
import dagger.hilt.android.internal.builders.FragmentComponentBuilder;
import dagger.hilt.android.internal.builders.ServiceComponentBuilder;
import dagger.hilt.android.internal.builders.ViewComponentBuilder;
import dagger.hilt.android.internal.builders.ViewModelComponentBuilder;
import dagger.hilt.android.internal.builders.ViewWithFragmentComponentBuilder;
import dagger.hilt.android.internal.lifecycle.DefaultViewModelFactories;
import dagger.hilt.android.internal.lifecycle.DefaultViewModelFactories_InternalFactoryFactory_Factory;
import dagger.hilt.android.internal.managers.ActivityRetainedComponentManager_LifecycleModule_ProvideActivityRetainedLifecycleFactory;
import dagger.hilt.android.internal.managers.SavedStateHandleHolder;
import dagger.hilt.android.internal.modules.ApplicationContextModule;
import dagger.hilt.android.internal.modules.ApplicationContextModule_ProvideContextFactory;
import dagger.internal.DaggerGenerated;
import dagger.internal.DoubleCheck;
import dagger.internal.LazyClassKeyMap;
import dagger.internal.Preconditions;
import dagger.internal.Provider;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Generated;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;

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
public final class DaggerVoicePingApplication_HiltComponents_SingletonC {
  private DaggerVoicePingApplication_HiltComponents_SingletonC() {
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private ApplicationContextModule applicationContextModule;

    private Builder() {
    }

    public Builder applicationContextModule(ApplicationContextModule applicationContextModule) {
      this.applicationContextModule = Preconditions.checkNotNull(applicationContextModule);
      return this;
    }

    public VoicePingApplication_HiltComponents.SingletonC build() {
      Preconditions.checkBuilderRequirement(applicationContextModule, ApplicationContextModule.class);
      return new SingletonCImpl(applicationContextModule);
    }
  }

  private static final class ActivityRetainedCBuilder implements VoicePingApplication_HiltComponents.ActivityRetainedC.Builder {
    private final SingletonCImpl singletonCImpl;

    private SavedStateHandleHolder savedStateHandleHolder;

    private ActivityRetainedCBuilder(SingletonCImpl singletonCImpl) {
      this.singletonCImpl = singletonCImpl;
    }

    @Override
    public ActivityRetainedCBuilder savedStateHandleHolder(
        SavedStateHandleHolder savedStateHandleHolder) {
      this.savedStateHandleHolder = Preconditions.checkNotNull(savedStateHandleHolder);
      return this;
    }

    @Override
    public VoicePingApplication_HiltComponents.ActivityRetainedC build() {
      Preconditions.checkBuilderRequirement(savedStateHandleHolder, SavedStateHandleHolder.class);
      return new ActivityRetainedCImpl(singletonCImpl, savedStateHandleHolder);
    }
  }

  private static final class ActivityCBuilder implements VoicePingApplication_HiltComponents.ActivityC.Builder {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private Activity activity;

    private ActivityCBuilder(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
    }

    @Override
    public ActivityCBuilder activity(Activity activity) {
      this.activity = Preconditions.checkNotNull(activity);
      return this;
    }

    @Override
    public VoicePingApplication_HiltComponents.ActivityC build() {
      Preconditions.checkBuilderRequirement(activity, Activity.class);
      return new ActivityCImpl(singletonCImpl, activityRetainedCImpl, activity);
    }
  }

  private static final class FragmentCBuilder implements VoicePingApplication_HiltComponents.FragmentC.Builder {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private Fragment fragment;

    private FragmentCBuilder(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl, ActivityCImpl activityCImpl) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;
    }

    @Override
    public FragmentCBuilder fragment(Fragment fragment) {
      this.fragment = Preconditions.checkNotNull(fragment);
      return this;
    }

    @Override
    public VoicePingApplication_HiltComponents.FragmentC build() {
      Preconditions.checkBuilderRequirement(fragment, Fragment.class);
      return new FragmentCImpl(singletonCImpl, activityRetainedCImpl, activityCImpl, fragment);
    }
  }

  private static final class ViewWithFragmentCBuilder implements VoicePingApplication_HiltComponents.ViewWithFragmentC.Builder {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private final FragmentCImpl fragmentCImpl;

    private View view;

    private ViewWithFragmentCBuilder(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl, ActivityCImpl activityCImpl,
        FragmentCImpl fragmentCImpl) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;
      this.fragmentCImpl = fragmentCImpl;
    }

    @Override
    public ViewWithFragmentCBuilder view(View view) {
      this.view = Preconditions.checkNotNull(view);
      return this;
    }

    @Override
    public VoicePingApplication_HiltComponents.ViewWithFragmentC build() {
      Preconditions.checkBuilderRequirement(view, View.class);
      return new ViewWithFragmentCImpl(singletonCImpl, activityRetainedCImpl, activityCImpl, fragmentCImpl, view);
    }
  }

  private static final class ViewCBuilder implements VoicePingApplication_HiltComponents.ViewC.Builder {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private View view;

    private ViewCBuilder(SingletonCImpl singletonCImpl, ActivityRetainedCImpl activityRetainedCImpl,
        ActivityCImpl activityCImpl) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;
    }

    @Override
    public ViewCBuilder view(View view) {
      this.view = Preconditions.checkNotNull(view);
      return this;
    }

    @Override
    public VoicePingApplication_HiltComponents.ViewC build() {
      Preconditions.checkBuilderRequirement(view, View.class);
      return new ViewCImpl(singletonCImpl, activityRetainedCImpl, activityCImpl, view);
    }
  }

  private static final class ViewModelCBuilder implements VoicePingApplication_HiltComponents.ViewModelC.Builder {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private SavedStateHandle savedStateHandle;

    private ViewModelLifecycle viewModelLifecycle;

    private ViewModelCBuilder(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
    }

    @Override
    public ViewModelCBuilder savedStateHandle(SavedStateHandle handle) {
      this.savedStateHandle = Preconditions.checkNotNull(handle);
      return this;
    }

    @Override
    public ViewModelCBuilder viewModelLifecycle(ViewModelLifecycle viewModelLifecycle) {
      this.viewModelLifecycle = Preconditions.checkNotNull(viewModelLifecycle);
      return this;
    }

    @Override
    public VoicePingApplication_HiltComponents.ViewModelC build() {
      Preconditions.checkBuilderRequirement(savedStateHandle, SavedStateHandle.class);
      Preconditions.checkBuilderRequirement(viewModelLifecycle, ViewModelLifecycle.class);
      return new ViewModelCImpl(singletonCImpl, activityRetainedCImpl, savedStateHandle, viewModelLifecycle);
    }
  }

  private static final class ServiceCBuilder implements VoicePingApplication_HiltComponents.ServiceC.Builder {
    private final SingletonCImpl singletonCImpl;

    private Service service;

    private ServiceCBuilder(SingletonCImpl singletonCImpl) {
      this.singletonCImpl = singletonCImpl;
    }

    @Override
    public ServiceCBuilder service(Service service) {
      this.service = Preconditions.checkNotNull(service);
      return this;
    }

    @Override
    public VoicePingApplication_HiltComponents.ServiceC build() {
      Preconditions.checkBuilderRequirement(service, Service.class);
      return new ServiceCImpl(singletonCImpl, service);
    }
  }

  private static final class ViewWithFragmentCImpl extends VoicePingApplication_HiltComponents.ViewWithFragmentC {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private final FragmentCImpl fragmentCImpl;

    private final ViewWithFragmentCImpl viewWithFragmentCImpl = this;

    ViewWithFragmentCImpl(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl, ActivityCImpl activityCImpl,
        FragmentCImpl fragmentCImpl, View viewParam) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;
      this.fragmentCImpl = fragmentCImpl;


    }
  }

  private static final class FragmentCImpl extends VoicePingApplication_HiltComponents.FragmentC {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private final FragmentCImpl fragmentCImpl = this;

    FragmentCImpl(SingletonCImpl singletonCImpl, ActivityRetainedCImpl activityRetainedCImpl,
        ActivityCImpl activityCImpl, Fragment fragmentParam) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;


    }

    @Override
    public DefaultViewModelFactories.InternalFactoryFactory getHiltInternalFactoryFactory() {
      return activityCImpl.getHiltInternalFactoryFactory();
    }

    @Override
    public ViewWithFragmentComponentBuilder viewWithFragmentComponentBuilder() {
      return new ViewWithFragmentCBuilder(singletonCImpl, activityRetainedCImpl, activityCImpl, fragmentCImpl);
    }
  }

  private static final class ViewCImpl extends VoicePingApplication_HiltComponents.ViewC {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private final ViewCImpl viewCImpl = this;

    ViewCImpl(SingletonCImpl singletonCImpl, ActivityRetainedCImpl activityRetainedCImpl,
        ActivityCImpl activityCImpl, View viewParam) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;


    }
  }

  private static final class ActivityCImpl extends VoicePingApplication_HiltComponents.ActivityC {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl = this;

    ActivityCImpl(SingletonCImpl singletonCImpl, ActivityRetainedCImpl activityRetainedCImpl,
        Activity activityParam) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;


    }

    @Override
    public void injectMainActivity(MainActivity mainActivity) {
      injectMainActivity2(mainActivity);
    }

    @Override
    public DefaultViewModelFactories.InternalFactoryFactory getHiltInternalFactoryFactory() {
      return DefaultViewModelFactories_InternalFactoryFactory_Factory.newInstance(getViewModelKeys(), new ViewModelCBuilder(singletonCImpl, activityRetainedCImpl));
    }

    @Override
    public Map<Class<?>, Boolean> getViewModelKeys() {
      return LazyClassKeyMap.<Boolean>of(ImmutableMap.<String, Boolean>of(ChannelListViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, ChannelListViewModel_HiltModules.KeyModule.provide(), EventPickerViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, EventPickerViewModel_HiltModules.KeyModule.provide(), LoadingViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, LoadingViewModel_HiltModules.KeyModule.provide(), LoginViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, LoginViewModel_HiltModules.KeyModule.provide(), SettingsViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, SettingsViewModel_HiltModules.KeyModule.provide()));
    }

    @Override
    public ViewModelComponentBuilder getViewModelComponentBuilder() {
      return new ViewModelCBuilder(singletonCImpl, activityRetainedCImpl);
    }

    @Override
    public FragmentComponentBuilder fragmentComponentBuilder() {
      return new FragmentCBuilder(singletonCImpl, activityRetainedCImpl, activityCImpl);
    }

    @Override
    public ViewComponentBuilder viewComponentBuilder() {
      return new ViewCBuilder(singletonCImpl, activityRetainedCImpl, activityCImpl);
    }

    @CanIgnoreReturnValue
    private MainActivity injectMainActivity2(MainActivity instance) {
      MainActivity_MembersInjector.injectPreferencesManager(instance, singletonCImpl.preferencesManagerProvider.get());
      MainActivity_MembersInjector.injectHardwareKeyHandler(instance, singletonCImpl.hardwareKeyHandlerProvider.get());
      MainActivity_MembersInjector.injectPttManager(instance, singletonCImpl.pttManagerProvider.get());
      MainActivity_MembersInjector.injectChannelRepository(instance, singletonCImpl.channelRepositoryProvider.get());
      return instance;
    }
  }

  private static final class ViewModelCImpl extends VoicePingApplication_HiltComponents.ViewModelC {
    private final SavedStateHandle savedStateHandle;

    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ViewModelCImpl viewModelCImpl = this;

    Provider<ChannelListViewModel> channelListViewModelProvider;

    Provider<EventPickerViewModel> eventPickerViewModelProvider;

    Provider<LoadingViewModel> loadingViewModelProvider;

    Provider<LoginViewModel> loginViewModelProvider;

    Provider<SettingsViewModel> settingsViewModelProvider;

    ViewModelCImpl(SingletonCImpl singletonCImpl, ActivityRetainedCImpl activityRetainedCImpl,
        SavedStateHandle savedStateHandleParam, ViewModelLifecycle viewModelLifecycleParam) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.savedStateHandle = savedStateHandleParam;
      initialize(savedStateHandleParam, viewModelLifecycleParam);

    }

    GetEventsUseCase getEventsUseCase() {
      return new GetEventsUseCase(singletonCImpl.eventRepositoryProvider.get());
    }

    LoginUseCase loginUseCase() {
      return new LoginUseCase(singletonCImpl.authRepositoryProvider.get());
    }

    @SuppressWarnings("unchecked")
    private void initialize(final SavedStateHandle savedStateHandleParam,
        final ViewModelLifecycle viewModelLifecycleParam) {
      this.channelListViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 0);
      this.eventPickerViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 1);
      this.loadingViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 2);
      this.loginViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 3);
      this.settingsViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 4);
    }

    @Override
    public Map<Class<?>, javax.inject.Provider<ViewModel>> getHiltViewModelMap() {
      return LazyClassKeyMap.<javax.inject.Provider<ViewModel>>of(ImmutableMap.<String, javax.inject.Provider<ViewModel>>of(ChannelListViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) (channelListViewModelProvider)), EventPickerViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) (eventPickerViewModelProvider)), LoadingViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) (loadingViewModelProvider)), LoginViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) (loginViewModelProvider)), SettingsViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) (settingsViewModelProvider))));
    }

    @Override
    public Map<Class<?>, Object> getHiltViewModelAssistedMap() {
      return ImmutableMap.<Class<?>, Object>of();
    }

    private static final class SwitchingProvider<T> implements Provider<T> {
      private final SingletonCImpl singletonCImpl;

      private final ActivityRetainedCImpl activityRetainedCImpl;

      private final ViewModelCImpl viewModelCImpl;

      private final int id;

      SwitchingProvider(SingletonCImpl singletonCImpl, ActivityRetainedCImpl activityRetainedCImpl,
          ViewModelCImpl viewModelCImpl, int id) {
        this.singletonCImpl = singletonCImpl;
        this.activityRetainedCImpl = activityRetainedCImpl;
        this.viewModelCImpl = viewModelCImpl;
        this.id = id;
      }

      @Override
      @SuppressWarnings("unchecked")
      public T get() {
        switch (id) {
          case 0: // com.voiceping.android.presentation.channels.ChannelListViewModel
          return (T) new ChannelListViewModel(singletonCImpl.eventRepositoryProvider.get(), singletonCImpl.signalingClientProvider.get(), singletonCImpl.channelRepositoryProvider.get(), singletonCImpl.preferencesManagerProvider.get(), singletonCImpl.pttManagerProvider.get(), singletonCImpl.settingsRepositoryProvider.get(), singletonCImpl.audioRouterProvider.get(), singletonCImpl.networkMonitorProvider.get(), singletonCImpl.transmissionHistoryRepositoryProvider.get(), ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule), viewModelCImpl.savedStateHandle);

          case 1: // com.voiceping.android.presentation.events.EventPickerViewModel
          return (T) new EventPickerViewModel(viewModelCImpl.getEventsUseCase(), singletonCImpl.preferencesManagerProvider.get());

          case 2: // com.voiceping.android.presentation.loading.LoadingViewModel
          return (T) new LoadingViewModel(singletonCImpl.signalingClientProvider.get(), singletonCImpl.mediasoupClientProvider.get(), singletonCImpl.preferencesManagerProvider.get(), singletonCImpl.tokenManagerProvider.get(), singletonCImpl.authRepositoryProvider.get());

          case 3: // com.voiceping.android.presentation.login.LoginViewModel
          return (T) new LoginViewModel(viewModelCImpl.loginUseCase(), singletonCImpl.tokenManagerProvider.get());

          case 4: // com.voiceping.android.presentation.settings.SettingsViewModel
          return (T) new SettingsViewModel(singletonCImpl.settingsRepositoryProvider.get(), singletonCImpl.audioRouterProvider.get());

          default: throw new AssertionError(id);
        }
      }
    }
  }

  private static final class ActivityRetainedCImpl extends VoicePingApplication_HiltComponents.ActivityRetainedC {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl = this;

    Provider<ActivityRetainedLifecycle> provideActivityRetainedLifecycleProvider;

    ActivityRetainedCImpl(SingletonCImpl singletonCImpl,
        SavedStateHandleHolder savedStateHandleHolderParam) {
      this.singletonCImpl = singletonCImpl;

      initialize(savedStateHandleHolderParam);

    }

    @SuppressWarnings("unchecked")
    private void initialize(final SavedStateHandleHolder savedStateHandleHolderParam) {
      this.provideActivityRetainedLifecycleProvider = DoubleCheck.provider(new SwitchingProvider<ActivityRetainedLifecycle>(singletonCImpl, activityRetainedCImpl, 0));
    }

    @Override
    public ActivityComponentBuilder activityComponentBuilder() {
      return new ActivityCBuilder(singletonCImpl, activityRetainedCImpl);
    }

    @Override
    public ActivityRetainedLifecycle getActivityRetainedLifecycle() {
      return provideActivityRetainedLifecycleProvider.get();
    }

    private static final class SwitchingProvider<T> implements Provider<T> {
      private final SingletonCImpl singletonCImpl;

      private final ActivityRetainedCImpl activityRetainedCImpl;

      private final int id;

      SwitchingProvider(SingletonCImpl singletonCImpl, ActivityRetainedCImpl activityRetainedCImpl,
          int id) {
        this.singletonCImpl = singletonCImpl;
        this.activityRetainedCImpl = activityRetainedCImpl;
        this.id = id;
      }

      @Override
      @SuppressWarnings("unchecked")
      public T get() {
        switch (id) {
          case 0: // dagger.hilt.android.ActivityRetainedLifecycle
          return (T) ActivityRetainedComponentManager_LifecycleModule_ProvideActivityRetainedLifecycleFactory.provideActivityRetainedLifecycle();

          default: throw new AssertionError(id);
        }
      }
    }
  }

  private static final class ServiceCImpl extends VoicePingApplication_HiltComponents.ServiceC {
    private final SingletonCImpl singletonCImpl;

    private final ServiceCImpl serviceCImpl = this;

    ServiceCImpl(SingletonCImpl singletonCImpl, Service serviceParam) {
      this.singletonCImpl = singletonCImpl;


    }

    @Override
    public void injectAudioCaptureService(AudioCaptureService audioCaptureService) {
    }

    @Override
    public void injectChannelMonitoringService(ChannelMonitoringService channelMonitoringService) {
    }
  }

  private static final class SingletonCImpl extends VoicePingApplication_HiltComponents.SingletonC {
    private final ApplicationContextModule applicationContextModule;

    private final SingletonCImpl singletonCImpl = this;

    Provider<PreferencesManager> preferencesManagerProvider;

    Provider<SettingsRepository> settingsRepositoryProvider;

    Provider<HardwareKeyHandler> hardwareKeyHandlerProvider;

    Provider<Gson> provideGsonProvider;

    Provider<NetworkMonitor> networkMonitorProvider;

    Provider<SignalingClient> signalingClientProvider;

    Provider<MediasoupClient> mediasoupClientProvider;

    Provider<AudioCaptureManager> audioCaptureManagerProvider;

    Provider<AudioRouter> audioRouterProvider;

    Provider<PttManager> pttManagerProvider;

    Provider<TonePlayer> tonePlayerProvider;

    Provider<HapticFeedback> hapticFeedbackProvider;

    Provider<AudioDeviceManager> audioDeviceManagerProvider;

    Provider<MediaButtonHandler> mediaButtonHandlerProvider;

    Provider<ChannelRepository> channelRepositoryProvider;

    Provider<SessionCookieJar> provideSessionCookieJarProvider;

    Provider<OkHttpClient> provideOkHttpClientProvider;

    Provider<Retrofit> provideRetrofitProvider;

    Provider<EventApi> provideEventApiProvider;

    Provider<VoicePingDatabase> provideDatabaseProvider;

    Provider<EventRepository> eventRepositoryProvider;

    Provider<TransmissionHistoryRepository> transmissionHistoryRepositoryProvider;

    Provider<TokenManager> tokenManagerProvider;

    Provider<AuthApi> provideAuthApiProvider;

    Provider<AuthRepository> authRepositoryProvider;

    SingletonCImpl(ApplicationContextModule applicationContextModuleParam) {
      this.applicationContextModule = applicationContextModuleParam;
      initialize(applicationContextModuleParam);

    }

    EventDao eventDao() {
      return AppModule_ProvideEventDaoFactory.provideEventDao(provideDatabaseProvider.get());
    }

    ChannelDao channelDao() {
      return AppModule_ProvideChannelDaoFactory.provideChannelDao(provideDatabaseProvider.get());
    }

    @SuppressWarnings("unchecked")
    private void initialize(final ApplicationContextModule applicationContextModuleParam) {
      this.preferencesManagerProvider = DoubleCheck.provider(new SwitchingProvider<PreferencesManager>(singletonCImpl, 0));
      this.settingsRepositoryProvider = DoubleCheck.provider(new SwitchingProvider<SettingsRepository>(singletonCImpl, 2));
      this.hardwareKeyHandlerProvider = DoubleCheck.provider(new SwitchingProvider<HardwareKeyHandler>(singletonCImpl, 1));
      this.provideGsonProvider = DoubleCheck.provider(new SwitchingProvider<Gson>(singletonCImpl, 5));
      this.networkMonitorProvider = DoubleCheck.provider(new SwitchingProvider<NetworkMonitor>(singletonCImpl, 6));
      this.signalingClientProvider = DoubleCheck.provider(new SwitchingProvider<SignalingClient>(singletonCImpl, 4));
      this.mediasoupClientProvider = DoubleCheck.provider(new SwitchingProvider<MediasoupClient>(singletonCImpl, 7));
      this.audioCaptureManagerProvider = DoubleCheck.provider(new SwitchingProvider<AudioCaptureManager>(singletonCImpl, 8));
      this.audioRouterProvider = DoubleCheck.provider(new SwitchingProvider<AudioRouter>(singletonCImpl, 9));
      this.pttManagerProvider = DoubleCheck.provider(new SwitchingProvider<PttManager>(singletonCImpl, 3));
      this.tonePlayerProvider = DoubleCheck.provider(new SwitchingProvider<TonePlayer>(singletonCImpl, 11));
      this.hapticFeedbackProvider = DoubleCheck.provider(new SwitchingProvider<HapticFeedback>(singletonCImpl, 12));
      this.audioDeviceManagerProvider = DoubleCheck.provider(new SwitchingProvider<AudioDeviceManager>(singletonCImpl, 13));
      this.mediaButtonHandlerProvider = DoubleCheck.provider(new SwitchingProvider<MediaButtonHandler>(singletonCImpl, 14));
      this.channelRepositoryProvider = DoubleCheck.provider(new SwitchingProvider<ChannelRepository>(singletonCImpl, 10));
      this.provideSessionCookieJarProvider = DoubleCheck.provider(new SwitchingProvider<SessionCookieJar>(singletonCImpl, 19));
      this.provideOkHttpClientProvider = DoubleCheck.provider(new SwitchingProvider<OkHttpClient>(singletonCImpl, 18));
      this.provideRetrofitProvider = DoubleCheck.provider(new SwitchingProvider<Retrofit>(singletonCImpl, 17));
      this.provideEventApiProvider = DoubleCheck.provider(new SwitchingProvider<EventApi>(singletonCImpl, 16));
      this.provideDatabaseProvider = DoubleCheck.provider(new SwitchingProvider<VoicePingDatabase>(singletonCImpl, 20));
      this.eventRepositoryProvider = DoubleCheck.provider(new SwitchingProvider<EventRepository>(singletonCImpl, 15));
      this.transmissionHistoryRepositoryProvider = DoubleCheck.provider(new SwitchingProvider<TransmissionHistoryRepository>(singletonCImpl, 21));
      this.tokenManagerProvider = DoubleCheck.provider(new SwitchingProvider<TokenManager>(singletonCImpl, 22));
      this.provideAuthApiProvider = DoubleCheck.provider(new SwitchingProvider<AuthApi>(singletonCImpl, 24));
      this.authRepositoryProvider = DoubleCheck.provider(new SwitchingProvider<AuthRepository>(singletonCImpl, 23));
    }

    @Override
    public void injectVoicePingApplication(VoicePingApplication voicePingApplication) {
    }

    @Override
    public Set<Boolean> getDisableFragmentGetContextFix() {
      return ImmutableSet.<Boolean>of();
    }

    @Override
    public ActivityRetainedComponentBuilder retainedComponentBuilder() {
      return new ActivityRetainedCBuilder(singletonCImpl);
    }

    @Override
    public ServiceComponentBuilder serviceComponentBuilder() {
      return new ServiceCBuilder(singletonCImpl);
    }

    private static final class SwitchingProvider<T> implements Provider<T> {
      private final SingletonCImpl singletonCImpl;

      private final int id;

      SwitchingProvider(SingletonCImpl singletonCImpl, int id) {
        this.singletonCImpl = singletonCImpl;
        this.id = id;
      }

      @Override
      @SuppressWarnings("unchecked")
      public T get() {
        switch (id) {
          case 0: // com.voiceping.android.data.storage.PreferencesManager
          return (T) new PreferencesManager(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule));

          case 1: // com.voiceping.android.data.hardware.HardwareKeyHandler
          return (T) new HardwareKeyHandler(singletonCImpl.settingsRepositoryProvider.get());

          case 2: // com.voiceping.android.data.storage.SettingsRepository
          return (T) new SettingsRepository(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule));

          case 3: // com.voiceping.android.data.ptt.PttManager
          return (T) new PttManager(singletonCImpl.signalingClientProvider.get(), singletonCImpl.mediasoupClientProvider.get(), singletonCImpl.audioCaptureManagerProvider.get(), singletonCImpl.audioRouterProvider.get(), ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule));

          case 4: // com.voiceping.android.data.network.SignalingClient
          return (T) new SignalingClient(singletonCImpl.provideGsonProvider.get(), singletonCImpl.networkMonitorProvider.get());

          case 5: // com.google.gson.Gson
          return (T) AppModule_ProvideGsonFactory.provideGson();

          case 6: // com.voiceping.android.data.network.NetworkMonitor
          return (T) new NetworkMonitor(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule));

          case 7: // com.voiceping.android.data.network.MediasoupClient
          return (T) new MediasoupClient(singletonCImpl.signalingClientProvider.get(), ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule));

          case 8: // com.voiceping.android.data.audio.AudioCaptureManager
          return (T) new AudioCaptureManager();

          case 9: // com.voiceping.android.data.audio.AudioRouter
          return (T) new AudioRouter(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule));

          case 10: // com.voiceping.android.data.repository.ChannelRepository
          return (T) new ChannelRepository(singletonCImpl.signalingClientProvider.get(), singletonCImpl.mediasoupClientProvider.get(), singletonCImpl.audioRouterProvider.get(), singletonCImpl.pttManagerProvider.get(), singletonCImpl.tonePlayerProvider.get(), singletonCImpl.hapticFeedbackProvider.get(), singletonCImpl.settingsRepositoryProvider.get(), singletonCImpl.audioDeviceManagerProvider.get(), singletonCImpl.mediaButtonHandlerProvider.get(), singletonCImpl.networkMonitorProvider.get(), ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule));

          case 11: // com.voiceping.android.data.audio.TonePlayer
          return (T) new TonePlayer(singletonCImpl.settingsRepositoryProvider.get());

          case 12: // com.voiceping.android.data.audio.HapticFeedback
          return (T) new HapticFeedback(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule));

          case 13: // com.voiceping.android.data.audio.AudioDeviceManager
          return (T) new AudioDeviceManager(singletonCImpl.audioRouterProvider.get(), ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule));

          case 14: // com.voiceping.android.data.hardware.MediaButtonHandler
          return (T) new MediaButtonHandler(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule));

          case 15: // com.voiceping.android.data.repository.EventRepository
          return (T) new EventRepository(singletonCImpl.provideEventApiProvider.get(), singletonCImpl.eventDao(), singletonCImpl.channelDao());

          case 16: // com.voiceping.android.data.api.EventApi
          return (T) EventModule_ProvideEventApiFactory.provideEventApi(singletonCImpl.provideRetrofitProvider.get());

          case 17: // retrofit2.Retrofit
          return (T) AuthModule_ProvideRetrofitFactory.provideRetrofit(singletonCImpl.provideOkHttpClientProvider.get(), singletonCImpl.provideGsonProvider.get());

          case 18: // okhttp3.OkHttpClient
          return (T) AppModule_ProvideOkHttpClientFactory.provideOkHttpClient(singletonCImpl.provideSessionCookieJarProvider.get());

          case 19: // com.voiceping.android.di.SessionCookieJar
          return (T) AppModule_ProvideSessionCookieJarFactory.provideSessionCookieJar();

          case 20: // com.voiceping.android.data.database.VoicePingDatabase
          return (T) AppModule_ProvideDatabaseFactory.provideDatabase(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule));

          case 21: // com.voiceping.android.data.repository.TransmissionHistoryRepository
          return (T) new TransmissionHistoryRepository();

          case 22: // com.voiceping.android.data.storage.TokenManager
          return (T) new TokenManager(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule));

          case 23: // com.voiceping.android.data.repository.AuthRepository
          return (T) new AuthRepository(singletonCImpl.provideAuthApiProvider.get(), singletonCImpl.tokenManagerProvider.get(), singletonCImpl.preferencesManagerProvider.get());

          case 24: // com.voiceping.android.data.api.AuthApi
          return (T) AuthModule_ProvideAuthApiFactory.provideAuthApi(singletonCImpl.provideRetrofitProvider.get());

          default: throw new AssertionError(id);
        }
      }
    }
  }
}
