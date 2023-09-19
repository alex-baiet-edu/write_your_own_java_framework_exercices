package com.github.forax.framework.injector;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Constructor;
import java.util.*;
import java.util.function.Supplier;

public final class InjectorRegistry {
  private Map<Class<?>, Supplier<?>> instances = new HashMap<>();

  public <T> T lookupInstance(Class<T> type) {
    Objects.requireNonNull(type);
    var instance = instances.get(type);
    if (instance == null) {
      throw new IllegalStateException("Not in the map.");
    }
    return type.cast(instance.get());
  }

  public <T> void registerInstance(Class<T> type, T instance) {
    Objects.requireNonNull(type);
    Objects.requireNonNull(instance);

    instances.putIfAbsent(type, () -> instance);
  }

  public <T> void registerProvider(Class<T> type, Supplier<T> supplier) {
    Objects.requireNonNull(type);
    Objects.requireNonNull(supplier);

    instances.putIfAbsent(type, supplier);
  }

  public <T> void registerProviderClass(Class<T> type, Class<? extends T> providerClass) {
    Objects.requireNonNull(type);
    Objects.requireNonNull(providerClass);

    var constructor = findInjectableConstructor(providerClass).orElseGet(() -> Utils.defaultConstructor(providerClass));

    var properties = findInjectableProperties(providerClass);

    instances.putIfAbsent(type, () -> {
      var arguments = Arrays.stream(constructor.getParameterTypes())
        .map(this::lookupInstance)
        .toArray();

      // Génération instance
      var instance = Utils.newInstance(constructor, arguments);

      for (var prop : properties) {
        var setter = prop.getWriteMethod();
        var paramType = prop.getPropertyType();
        var arg = lookupInstance(paramType);
        Utils.invokeMethod(instance, setter, arg);
      }

      return providerClass.cast(instance);
    });
  }

  public void registerProviderClass(Class providerClass) {
    registerProviderClass(providerClass, providerClass);
  }

  private Optional<Constructor<?>> findInjectableConstructor(Class<?> providerClass) {
    var constructors = Arrays.stream(providerClass.getConstructors())
      .filter(constructor -> constructor.getAnnotation(Inject.class) != null)
      .toList();

    if (constructors.size() > 1) throw new IllegalStateException("Trop de constructeur avec l'annotation");
    return constructors.size() == 1 ? Optional.of(constructors.get(0)) : Optional.empty();
  }

  static List<PropertyDescriptor> findInjectableProperties(Class<?> type) {
    Objects.requireNonNull(type);
    var properties = Utils.beanInfo(type).getPropertyDescriptors();

    return Arrays.stream(properties)
      .filter(prop -> {
        var setter = prop.getWriteMethod();
        return setter != null && setter.getAnnotation(Inject.class) != null;
      })
      .toList();
  }


}