package io.searchpe.instantiators;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

public class BeanInstantiator<T> {

    private final Class<T> zClass;
    private final String[] headers;

    public BeanInstantiator(Class<T> zClass, String[] headers) {
        this.zClass = zClass;
        this.headers = headers;
    }

    public T create(Object[] columns) throws IllegalAccessException, NoSuchFieldException, InstantiationException {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < headers.length; i++) {
            map.put(headers[i], columns[i]);
        }

        return instantiate(zClass, map);
    }

    private T instantiate(Class<T> zClass, Map<String, Object> columns) throws IllegalAccessException, InstantiationException, NoSuchFieldException {
        T object = zClass.newInstance();
        for (Map.Entry<String, Object> entry : columns.entrySet()) {
            Field field = zClass.getDeclaredField(entry.getKey());
            field.setAccessible(true);
            field.set(object, entry.getValue());
        }
        return object;
    }

}
