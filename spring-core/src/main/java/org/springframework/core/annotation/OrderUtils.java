/*
 * Copyright 2002-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.core.annotation;

import org.springframework.core.annotation.MergedAnnotations.SearchStrategy;
import org.springframework.lang.Nullable;
import org.springframework.util.ConcurrentReferenceHashMap;

import java.lang.reflect.AnnotatedElement;
import java.util.Map;

/**
 * General utility for determining the order of an object based on its type declaration.
 * Handles Spring's {@link Order} annotation as well as {@link javax.annotation.Priority}.
 *
 * @author Stephane Nicoll
 * @author Juergen Hoeller
 * @since 4.1
 * @see Order
 * @see javax.annotation.Priority
 */
public abstract class OrderUtils {

	/** Cache marker for a non-annotated Class. */
	private static final Object NOT_ANNOTATED = new Object();

	private static final String JAVAX_PRIORITY_ANNOTATION = "javax.annotation.Priority";

	/** Cache for @Order value (or NOT_ANNOTATED marker) per Class. */
	private static final Map<AnnotatedElement, Object> orderCache = new ConcurrentReferenceHashMap<>(64);


	/**
	 * Return the order on the specified {@code type}, or the specified
	 * default value if none can be found.
	 * <p>Takes care of {@link Order @Order} and {@code @javax.annotation.Priority}.
	 * @param type the type to handle
	 * @return the priority value, or the specified default order if none can be found
	 * @since 5.0
	 * @see #getPriority(Class)
	 */
	public static int getOrder(Class<?> type, int defaultOrder) {
		Integer order = getOrder(type);
		return (order != null ? order : defaultOrder);
	}

	/**
	 * Return the order on the specified {@code type}, or the specified
	 * default value if none can be found.
	 * <p>Takes care of {@link Order @Order} and {@code @javax.annotation.Priority}.
	 * @param type the type to handle
	 * @return the priority value, or the specified default order if none can be found
	 * @see #getPriority(Class)
	 */
	@Nullable
	public static Integer getOrder(Class<?> type, @Nullable Integer defaultOrder) {
		Integer order = getOrder(type);
		return (order != null ? order : defaultOrder);
	}

	/**
	 * Return the order on the specified {@code type}.
	 * <p>Takes care of {@link Order @Order} and {@code @javax.annotation.Priority}.
	 * @param type the type to handle
	 * @return the order value, or {@code null} if none can be found
	 * @see #getPriority(Class)
	 */
	@Nullable
	public static Integer getOrder(Class<?> type) {
		return getOrderFromAnnotations(type, MergedAnnotations.from(type, SearchStrategy.TYPE_HIERARCHY));
	}

	/**
	 * Return the order from the specified annotations collection.
	 * <p>Takes care of {@link Order @Order} and
	 * {@code @javax.annotation.Priority}.
	 * @param element the source element
	 * @param annotations the annotation to consider
	 * @return the order value, or {@code null} if none can be found
	 */
	@Nullable
	static Integer getOrderFromAnnotations(AnnotatedElement element, MergedAnnotations annotations) {

		// 如果element非Class，则从以获取到的注解集合annotations中直接获取orderValue
		// 非Class的element的orderValue，不缓存
		if (!(element instanceof Class)) {
			return findOrder(annotations);
		}

		// 获取element的orderValue缓存结果，如果存在则返回
		Object cached = orderCache.get(element);
		if (cached != null) {
			return (cached instanceof Integer ? (Integer) cached : null);
		}

		// 获取orderValue
		Integer result = findOrder(annotations);

		// 设置缓存，如果orderValue为空，则缓存设置为特定的对象NOT_ANNOTATED
		orderCache.put(element, result != null ? result : NOT_ANNOTATED);

		// 返回结果
		return result;
	}

	@Nullable
	private static Integer findOrder(MergedAnnotations annotations) {

		// 获取Order注解
		MergedAnnotation<Order> orderAnnotation = annotations.get(Order.class);

		// 如果存在Order注解，则获取配置的orderValue
		if (orderAnnotation.isPresent()) {
			return orderAnnotation.getInt(MergedAnnotation.VALUE);
		}

		// 如果Order注解为空，则获取Priority注解
		MergedAnnotation<?> priorityAnnotation = annotations.get(JAVAX_PRIORITY_ANNOTATION);

		// 如果存在Priority注解，则返回配置的值
		if (priorityAnnotation.isPresent()) {
			return priorityAnnotation.getInt(MergedAnnotation.VALUE);
		}

		// 既不存在Order注解，也不存在Priority注解，直接返回空
		return null;
	}

	/**
	 * Return the value of the {@code javax.annotation.Priority} annotation
	 * declared on the specified type, or {@code null} if none.
	 * @param type the type to handle
	 * @return the priority value if the annotation is declared, or {@code null} if none
	 */
	@Nullable
	public static Integer getPriority(Class<?> type) {
		return MergedAnnotations.from(type, SearchStrategy.TYPE_HIERARCHY).get(JAVAX_PRIORITY_ANNOTATION)
				.getValue(MergedAnnotation.VALUE, Integer.class).orElse(null);
	}

}
