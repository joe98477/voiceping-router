package com.voiceping.android.data.repository;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
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
public final class TransmissionHistoryRepository_Factory implements Factory<TransmissionHistoryRepository> {
  @Override
  public TransmissionHistoryRepository get() {
    return newInstance();
  }

  public static TransmissionHistoryRepository_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static TransmissionHistoryRepository newInstance() {
    return new TransmissionHistoryRepository();
  }

  private static final class InstanceHolder {
    static final TransmissionHistoryRepository_Factory INSTANCE = new TransmissionHistoryRepository_Factory();
  }
}
