package com.github.forax.framework.mapper;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public final class JSONWriter {
  private interface Generator {
    String generate(JSONWriter writer, Object bean);
  }

  private static final ClassValue<List<Generator>> CACHE = new ClassValue<List<Generator>>() {
    @Override
    protected List<Generator> computeValue(Class<?> type) {
      var beanInfo = Utils.beanInfo(type).getPropertyDescriptors();

      return Arrays.stream(beanInfo)
              .filter(prop -> !prop.getName().equals("class"))
              .<Generator>map(prop -> {
                        String key;
                        var annotation = prop.getReadMethod().getAnnotation(JSONProperty.class);
                        if (annotation != null) {
                          key = String.format("\"%s\": ", annotation.value());
                        } else {
                          key = String.format("\"%s\": ", prop.getName());
                        }
                        return (writer, bean) -> key + writer.toJSON(Utils.invokeMethod(bean, prop.getReadMethod()));
                      }
              ).toList();

    }
  };

  public String toJSON(Object o) {
    return switch (o) {
      case null -> "null";
      case Boolean bool -> bool.toString();
      case Integer integer -> integer.toString();
      case String str -> '"' + str + '"';
      case Double dou -> dou.toString();
      default -> {
        yield objectToJson(o);
      }
    };
  }

  private String objectToJson(Object o) {
    BeanInfo beanInfo;

    List<Generator> properties = CACHE.get(o.getClass());

    /*return "{" + Arrays.stream(properties)
            .filter(prop -> !prop.getName().equals("class"))
            .map(prop -> String.format("\"%s\": %s", prop.getName(), toJSON(Utils.invokeMethod(o, prop.getReadMethod()))))
            .collect(Collectors.joining(", ")) + "}";*/
    return "{" + properties.stream()
            .map(generator -> generator.generate(this, o))
            .collect(Collectors.joining(", ")) + "}";
  }
}
