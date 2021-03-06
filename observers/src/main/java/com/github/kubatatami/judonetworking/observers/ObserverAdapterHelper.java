package com.github.kubatatami.judonetworking.observers;

import android.content.Context;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.github.kubatatami.judonetworking.exceptions.JudoException;
import com.github.kubatatami.judonetworking.utils.ReflectionCache;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: jbogacki
 * Date: 25.10.2013
 * Time: 10:15
 * To change this template use File | Settings | File Templates.
 */
public class ObserverAdapterHelper {

    protected Context context;
    protected LayoutInflater layoutInflater;
    private static final String splitter = "\\.";

    public ObserverAdapterHelper(Context context) {
        this.context = context;
        this.layoutInflater = LayoutInflater.from(context);
    }

    protected static class DataSourceOrTarget {
        protected Context context;
        protected Field field;
        protected Method method;
        protected List<Field> fields;

        public DataSourceOrTarget(Context context, List<Field> fields, Field field) {
            this.context = context;
            this.field = field;
            this.fields = fields;
        }

        public DataSourceOrTarget(Context context, List<Field> fields, Method method) {
            this.context = context;
            this.method = method;
            this.fields = fields;
        }

        public boolean isSource() {
            return field != null || !method.getReturnType().equals(Void.TYPE);
        }

        protected Object findObject(Object item) throws IllegalAccessException {
            for (Field field : fields) {
                item = field.get(item);
            }
            return item;
        }

        public String getValue(Object item) {
            try {
                Object result;
                item = findObject(item);
                if (field != null) {
                    result = field.get(item);
                } else if (method.getParameterTypes().length == 1) {
                    result = method.invoke(item, context);
                } else {
                    result = method.invoke(item);
                }

                return result != null ? result.toString() : "null";

            } catch (Exception e) {
                ExceptionHandler.throwRuntimeException(e);
                return null;
            }
        }

        public void setValue(Object item, View view) {
            try {
                item = findObject(item);
                method.invoke(item, view);
            } catch (Exception e) {
                ExceptionHandler.throwRuntimeException(e);
            }
        }

    }


    public View getView(int layout, Object item, View convertView, ViewGroup parent) {
        return getView(layout, item, convertView, parent, null);
    }

    public static boolean isInnerClass(Class<?> clazz) {
        return clazz.isMemberClass() && !Modifier.isStatic(clazz.getModifiers());
    }

    public View getView(int layout, View convertView, ViewGroup parent, Class<?> holderClass) {
        return getView(layout, null, convertView, parent, holderClass);
    }

    public View getView(int layout, View convertView, ViewGroup parent) {
        return getView(layout, null, convertView, parent, null);
    }

    @SuppressWarnings("unchecked")
    public View getView(int layout, Object item, View convertView, ViewGroup parent, Class<?> holderClass) {
        try {
            List<Pair<View, DataSourceOrTarget>> dataSources;

            if (convertView == null) {
                convertView = layoutInflater.inflate(layout, parent, false);
                dataSources = new ArrayList<>();
                if (item != null) {
                    findViewTag(convertView, dataSources, item.getClass());
                }
                convertView.setTag(layout, dataSources);
                if (holderClass != null) {
                    if (isInnerClass(holderClass)) {
                        throw new JudoException("Inner holder class must be static!");
                    }
                    Constructor<?> constructor = holderClass.getDeclaredConstructors()[0];
                    constructor.setAccessible(true);
                    Object holder = constructor.newInstance();
                    for (Field field : holderClass.getDeclaredFields()) {
                        HolderView viewById = ReflectionCache.getAnnotation(field, HolderView.class);
                        if (viewById != null) {
                            field.setAccessible(true);
                            int res;
                            if (viewById.value() != 0) {
                                res = viewById.value();
                            } else if (!viewById.resName().equals("")) {
                                res = context.getResources().getIdentifier(viewById.resName(), "id", context.getPackageName());
                            } else {
                                res = context.getResources().getIdentifier(field.getName(), "id", context.getPackageName());
                            }
                            field.set(holder, convertView.findViewById(res));
                        }
                        HolderCallback holderCallback = ReflectionCache.getAnnotation(field, HolderCallback.class);
                        if (holderCallback != null) {
                            field.setAccessible(true);
                            View view = convertView.findViewById(holderCallback.value());
                            Object callback = field.getType().getConstructor(View.class).newInstance(view);
                            field.set(holder, callback);
                        }
                    }
                    convertView.setTag(holder);
                }

            } else {
                dataSources = (List<Pair<View, DataSourceOrTarget>>) convertView.getTag(layout);
            }
            for (Pair<View, DataSourceOrTarget> pair : dataSources) {
                if (pair.second.isSource()) {
                    ((TextView) pair.first).setText(pair.second.getValue(item));
                } else {
                    pair.second.setValue(item, pair.first);
                }
            }
        } catch (Exception e) {
            ExceptionHandler.throwRuntimeException(e);
        }
        return convertView;
    }


    private void findViewTag(View view, List<Pair<View, DataSourceOrTarget>> data, Class<?> itemClass) throws JudoException {
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View viewElem = group.getChildAt(i);
                findViewTag(viewElem, data, itemClass);
            }
        } else {
            linkViewTag(view, data, itemClass);
        }
    }

    @SuppressWarnings("unchecked")
    private void linkViewTag(final View view, List<Pair<View, DataSourceOrTarget>> data, Class<?> itemClass) throws JudoException {

        if (view.getTag() != null && view.getTag() instanceof String) {
            String tag = (String) view.getTag();
            if (tag.matches("\\[.*\\]")) {
                tag = tag.substring(2, tag.length() - 1);
                DataSourceOrTarget dataSourceOrTarget = getDataSource(tag, itemClass);
                if (dataSourceOrTarget.isSource() && !(view instanceof TextView)) {
                    throw new JudoException("Method which returns value must be link with TextView");
                }
                data.add(new Pair<>(view, dataSourceOrTarget));
            }
        }

    }

    private DataSourceOrTarget getDataSource(String fieldName, Class<?> clazz) {
        int i = 0;
        Field field;
        List<Field> fields = new ArrayList<>();
        String parts[] = fieldName.split(splitter);
        for (String part : parts) {
            i++;
            if (i != parts.length) {
                try {
                    field = getField(part, clazz);
                    clazz = field.getType();
                    fields.add(field);
                } catch (NoSuchFieldException e) {
                    ExceptionHandler.throwRuntimeException(e);
                }
            } else {
                try {
                    field = getField(part, clazz);
                    return new DataSourceOrTarget(context, fields, field);
                } catch (NoSuchFieldException e) {
                    try {
                        Method method = getMethod(part, clazz);
                        return new DataSourceOrTarget(context, fields, method);
                    } catch (NoSuchFieldException e1) {
                        ExceptionHandler.throwRuntimeException(e);
                    }


                }
            }

        }
        return null;
    }

    private static Field getField(String fieldName, Class<?> objectClass) throws NoSuchFieldException {
        Field field = null;
        while (objectClass != null && field == null) {
            try {
                field = objectClass.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                objectClass = objectClass.getSuperclass();
            }
        }
        if (field != null) {
            field.setAccessible(true);
        } else {
            throw new NoSuchFieldException(fieldName);
        }
        return field;
    }


    static Method getMethod(String fieldName, Class<?> objectClass) throws NoSuchFieldException {
        Method finalMethod = null;
        while (objectClass != null && finalMethod == null) {
            for (Method method : objectClass.getDeclaredMethods()) {
                if (method.getName().equals(fieldName)) {
                    Class<?>[] paramsType = method.getParameterTypes();
                    if (paramsType.length == 0) {
                        finalMethod = method;
                        break;
                    } else if (paramsType.length == 1) {
                        if (paramsType[0].equals(Context.class) || View.class.isAssignableFrom(paramsType[0])) {
                            finalMethod = method;
                            break;
                        }
                    }

                }
            }
            if (finalMethod == null) {
                objectClass = objectClass.getSuperclass();
            }
        }
        if (finalMethod == null) {
            throw new NoSuchFieldException(fieldName);
        }
        return finalMethod;
    }
}
