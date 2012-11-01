/*
 * Copyright 2002-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ch.aonyx.broker.ib.api.util;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.WeakHashMap;

import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

/**
 * General utility methods for working with annotations, handling bridge methods (which the compiler generates for
 * generic declarations) as well as super methods (for optional &quot;annotation inheritance&quot;). Note that none of
 * this is provided by the JDK's introspection facilities themselves.
 * 
 * <p>
 * As a general rule for runtime-retained annotations (e.g. for transaction control, authorization or service exposure),
 * always use the lookup methods on this class (e.g., {@link #findAnnotation(Method, Class)},
 * {@link #getAnnotation(Method, Class)}, and {@link #getAnnotations(Method)}) instead of the plain annotation lookup
 * methods in the JDK. You can still explicitly choose between lookup on the given class level only (
 * {@link #getAnnotation(Method, Class)}) and lookup in the entire inheritance hierarchy of the given method (
 * {@link #findAnnotation(Method, Class)}).
 * 
 * @author Rob Harrop
 * @author Juergen Hoeller
 * @author Sam Brannen
 * @author Mark Fisher
 * @author Chris Beams
 * @since 2.0
 * @see java.lang.reflect.Method#getAnnotations()
 * @see java.lang.reflect.Method#getAnnotation(Class)
 */
public abstract class AnnotationUtils {

    /** The attribute name for annotations with a single element */
    static final String VALUE = "value";

    private static final Map<Class<?>, Boolean> ANNOTATED_INTERFACE_CACHE = new WeakHashMap<Class<?>, Boolean>();

    /**
     * Get a single {@link Annotation} of {@code annotationType} from the supplied Method, Constructor or Field.
     * Meta-annotations will be searched if the annotation is not declared locally on the supplied element.
     * 
     * @param ae
     *            the Method, Constructor or Field from which to get the annotation
     * @param annotationType
     *            the annotation class to look for, both locally and as a meta-annotation
     * @return the matching annotation or {@code null} if not found
     * @since 3.1
     */
    public static <T extends Annotation> T getAnnotation(final AnnotatedElement ae, final Class<T> annotationType) {
        T ann = ae.getAnnotation(annotationType);
        if (ann == null) {
            for (final Annotation metaAnn : ae.getAnnotations()) {
                ann = metaAnn.annotationType().getAnnotation(annotationType);
                if (ann != null) {
                    break;
                }
            }
        }
        return ann;
    }

    /**
     * Get a single {@link Annotation} of <code>annotationType</code> from the supplied {@link Method}, traversing its
     * super methods if no annotation can be found on the given method itself.
     * <p>
     * Annotations on methods are not inherited by default, so we need to handle this explicitly.
     * 
     * @param method
     *            the method to look for annotations on
     * @param annotationType
     *            the annotation class to look for
     * @return the annotation found, or <code>null</code> if none found
     */
    public static <A extends Annotation> A findAnnotation(final Method method, final Class<A> annotationType) {
        A annotation = getAnnotation(method, annotationType);
        Class<?> cl = method.getDeclaringClass();
        if (annotation == null) {
            annotation = searchOnInterfaces(method, annotationType, cl.getInterfaces());
        }
        while (annotation == null) {
            cl = cl.getSuperclass();
            if ((cl == null) || (cl == Object.class)) {
                break;
            }
            try {
                final Method equivalentMethod = cl.getDeclaredMethod(method.getName(), method.getParameterTypes());
                annotation = getAnnotation(equivalentMethod, annotationType);
                if (annotation == null) {
                    annotation = searchOnInterfaces(method, annotationType, cl.getInterfaces());
                }
            } catch (final NoSuchMethodException ex) {
                // We're done...
            }
        }
        return annotation;
    }

    private static <A extends Annotation> A searchOnInterfaces(final Method method, final Class<A> annotationType,
            final Class<?>[] ifcs) {
        A annotation = null;
        for (final Class<?> iface : ifcs) {
            if (isInterfaceWithAnnotatedMethods(iface)) {
                try {
                    final Method equivalentMethod = iface.getMethod(method.getName(), method.getParameterTypes());
                    annotation = getAnnotation(equivalentMethod, annotationType);
                } catch (final NoSuchMethodException ex) {
                    // Skip this interface - it doesn't have the method...
                }
                if (annotation != null) {
                    break;
                }
            }
        }
        return annotation;
    }

    private static boolean isInterfaceWithAnnotatedMethods(final Class<?> iface) {
        synchronized (ANNOTATED_INTERFACE_CACHE) {
            final Boolean flag = ANNOTATED_INTERFACE_CACHE.get(iface);
            if (flag != null) {
                return flag;
            }
            boolean found = false;
            for (final Method ifcMethod : iface.getMethods()) {
                if (ifcMethod.getAnnotations().length > 0) {
                    found = true;
                    break;
                }
            }
            ANNOTATED_INTERFACE_CACHE.put(iface, found);
            return found;
        }
    }

    /**
     * Find a single {@link Annotation} of <code>annotationType</code> from the supplied {@link Class}, traversing its
     * interfaces and superclasses if no annotation can be found on the given class itself.
     * <p>
     * This method explicitly handles class-level annotations which are not declared as
     * {@link java.lang.annotation.Inherited inherited} <i>as well as annotations on interfaces</i>.
     * <p>
     * The algorithm operates as follows: Searches for an annotation on the given class and returns it if found. Else
     * searches all interfaces that the given class declares, returning the annotation from the first matching
     * candidate, if any. Else proceeds with introspection of the superclass of the given class, checking the superclass
     * itself; if no annotation found there, proceeds with the interfaces that the superclass declares. Recursing up
     * through the entire superclass hierarchy if no match is found.
     * 
     * @param clazz
     *            the class to look for annotations on
     * @param annotationType
     *            the annotation class to look for
     * @return A tuple {@link Pair} containing the annotation on the left hand side and the class on the right hand side
     *         or <code>null</code> if none found
     */
    public static <A extends Annotation> Pair<A, Class<?>> findAnnotation(final Class<?> clazz,
            final Class<A> annotationType) {
        Validate.notNull(clazz, "Class must not be null");
        A annotation = clazz.getAnnotation(annotationType);
        if (annotation != null) {
            return new ImmutablePair<A, Class<?>>(annotation, clazz);
        }
        for (final Class<?> ifc : clazz.getInterfaces()) {
            final Pair<A, Class<?>> pair = findAnnotation(ifc, annotationType);
            if (pair != null) {
                annotation = pair.getLeft();
                if (annotation != null) {
                    return new ImmutablePair<A, Class<?>>(annotation, ifc);
                }
            }
        }
        if (!Annotation.class.isAssignableFrom(clazz)) {
            for (final Annotation ann : clazz.getAnnotations()) {
                final Pair<A, Class<?>> pair = findAnnotation(ann.annotationType(), annotationType);
                if (pair != null) {
                    annotation = pair.getLeft();
                    if (annotation != null) {
                        return new ImmutablePair<A, Class<?>>(annotation, ann.annotationType());
                    }
                }
            }
        }
        final Class<?> superClass = clazz.getSuperclass();
        if ((superClass == null) || (superClass == Object.class)) {
            return null;
        }
        return findAnnotation(superClass, annotationType);
    }

    /**
     * Find the first {@link Class} in the inheritance hierarchy of the specified <code>clazz</code> (including the
     * specified <code>clazz</code> itself) which declares an annotation for the specified <code>annotationType</code>,
     * or <code>null</code> if not found. If the supplied <code>clazz</code> is <code>null</code>, <code>null</code>
     * will be returned.
     * <p>
     * If the supplied <code>clazz</code> is an interface, only the interface itself will be checked; the inheritance
     * hierarchy for interfaces will not be traversed.
     * <p>
     * The standard {@link Class} API does not provide a mechanism for determining which class in an inheritance
     * hierarchy actually declares an {@link Annotation}, so we need to handle this explicitly.
     * 
     * @param annotationType
     *            the Class object corresponding to the annotation type
     * @param clazz
     *            the Class object corresponding to the class on which to check for the annotation, or <code>null</code>
     * @return the first {@link Class} in the inheritance hierarchy of the specified <code>clazz</code> which declares
     *         an annotation for the specified <code>annotationType</code>, or <code>null</code> if not found
     * @see Class#isAnnotationPresent(Class)
     * @see Class#getDeclaredAnnotations()
     */
    public static Class<?> findAnnotationDeclaringClass(final Class<? extends Annotation> annotationType,
            final Class<?> clazz) {
        Validate.notNull(annotationType, "Annotation type must not be null");
        if ((clazz == null) || clazz.equals(Object.class)) {
            return null;
        }
        return (isAnnotationDeclaredLocally(annotationType, clazz)) ? clazz : findAnnotationDeclaringClass(
                annotationType, clazz.getSuperclass());
    }

    /**
     * Determine whether an annotation for the specified <code>annotationType</code> is declared locally on the supplied
     * <code>clazz</code>. The supplied {@link Class} may represent any type.
     * <p>
     * Note: This method does <strong>not</strong> determine if the annotation is {@link java.lang.annotation.Inherited
     * inherited}. For greater clarity regarding inherited annotations, consider using
     * {@link #isAnnotationInherited(Class, Class)} instead.
     * 
     * @param annotationType
     *            the Class object corresponding to the annotation type
     * @param clazz
     *            the Class object corresponding to the class on which to check for the annotation
     * @return <code>true</code> if an annotation for the specified <code>annotationType</code> is declared locally on
     *         the supplied <code>clazz</code>
     * @see Class#getDeclaredAnnotations()
     * @see #isAnnotationInherited(Class, Class)
     */
    public static boolean isAnnotationDeclaredLocally(final Class<? extends Annotation> annotationType,
            final Class<?> clazz) {
        Validate.notNull(annotationType, "Annotation type must not be null");
        Validate.notNull(clazz, "Class must not be null");
        boolean declaredLocally = false;
        for (final Annotation annotation : Arrays.asList(clazz.getDeclaredAnnotations())) {
            if (annotation.annotationType().equals(annotationType)) {
                declaredLocally = true;
                break;
            }
        }
        return declaredLocally;
    }

    /**
     * Determine whether an annotation for the specified <code>annotationType</code> is present on the supplied
     * <code>clazz</code> and is {@link java.lang.annotation.Inherited inherited} i.e., not declared locally for the
     * class).
     * <p>
     * If the supplied <code>clazz</code> is an interface, only the interface itself will be checked. In accordance with
     * standard meta-annotation semantics, the inheritance hierarchy for interfaces will not be traversed. See the
     * {@link java.lang.annotation.Inherited JavaDoc} for the &#064;Inherited meta-annotation for further details
     * regarding annotation inheritance.
     * 
     * @param annotationType
     *            the Class object corresponding to the annotation type
     * @param clazz
     *            the Class object corresponding to the class on which to check for the annotation
     * @return <code>true</code> if an annotation for the specified <code>annotationType</code> is present on the
     *         supplied <code>clazz</code> and is {@link java.lang.annotation.Inherited inherited}
     * @see Class#isAnnotationPresent(Class)
     * @see #isAnnotationDeclaredLocally(Class, Class)
     */
    public static boolean isAnnotationInherited(final Class<? extends Annotation> annotationType, final Class<?> clazz) {
        Validate.notNull(annotationType, "Annotation type must not be null");
        Validate.notNull(clazz, "Class must not be null");
        return (clazz.isAnnotationPresent(annotationType) && !isAnnotationDeclaredLocally(annotationType, clazz));
    }

    /**
     * Retrieve the <em>value</em> of the <code>&quot;value&quot;</code> attribute of a single-element Annotation, given
     * an annotation instance.
     * 
     * @param annotation
     *            the annotation instance from which to retrieve the value
     * @return the attribute value, or <code>null</code> if not found
     * @see #getValue(Annotation, String)
     */
    public static Object getValue(final Annotation annotation) {
        return getValue(annotation, VALUE);
    }

    /**
     * Retrieve the <em>value</em> of a named Annotation attribute, given an annotation instance.
     * 
     * @param annotation
     *            the annotation instance from which to retrieve the value
     * @param attributeName
     *            the name of the attribute value to retrieve
     * @return the attribute value, or <code>null</code> if not found
     * @see #getValue(Annotation)
     */
    public static Object getValue(final Annotation annotation, final String attributeName) {
        try {
            final Method method = annotation.annotationType().getDeclaredMethod(attributeName, new Class[0]);
            return method.invoke(annotation);
        } catch (final Exception ex) {
            return null;
        }
    }

    /**
     * Retrieve the <em>default value</em> of the <code>&quot;value&quot;</code> attribute of a single-element
     * Annotation, given an annotation instance.
     * 
     * @param annotation
     *            the annotation instance from which to retrieve the default value
     * @return the default value, or <code>null</code> if not found
     * @see #getDefaultValue(Annotation, String)
     */
    public static Object getDefaultValue(final Annotation annotation) {
        return getDefaultValue(annotation, VALUE);
    }

    /**
     * Retrieve the <em>default value</em> of a named Annotation attribute, given an annotation instance.
     * 
     * @param annotation
     *            the annotation instance from which to retrieve the default value
     * @param attributeName
     *            the name of the attribute value to retrieve
     * @return the default value of the named attribute, or <code>null</code> if not found
     * @see #getDefaultValue(Class, String)
     */
    public static Object getDefaultValue(final Annotation annotation, final String attributeName) {
        return getDefaultValue(annotation.annotationType(), attributeName);
    }

    /**
     * Retrieve the <em>default value</em> of the <code>&quot;value&quot;</code> attribute of a single-element
     * Annotation, given the {@link Class annotation type}.
     * 
     * @param annotationType
     *            the <em>annotation type</em> for which the default value should be retrieved
     * @return the default value, or <code>null</code> if not found
     * @see #getDefaultValue(Class, String)
     */
    public static Object getDefaultValue(final Class<? extends Annotation> annotationType) {
        return getDefaultValue(annotationType, VALUE);
    }

    /**
     * Retrieve the <em>default value</em> of a named Annotation attribute, given the {@link Class annotation type}.
     * 
     * @param annotationType
     *            the <em>annotation type</em> for which the default value should be retrieved
     * @param attributeName
     *            the name of the attribute value to retrieve.
     * @return the default value of the named attribute, or <code>null</code> if not found
     * @see #getDefaultValue(Annotation, String)
     */
    public static Object getDefaultValue(final Class<? extends Annotation> annotationType, final String attributeName) {
        try {
            final Method method = annotationType.getDeclaredMethod(attributeName, new Class[0]);
            return method.getDefaultValue();
        } catch (final Exception ex) {
            return null;
        }
    }

}
