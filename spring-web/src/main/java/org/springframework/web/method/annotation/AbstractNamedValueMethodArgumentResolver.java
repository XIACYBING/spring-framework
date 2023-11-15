/*
 * Copyright 2002-2018 the original author or authors.
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

package org.springframework.web.method.annotation;

import org.springframework.beans.ConversionNotSupportedException;
import org.springframework.beans.TypeMismatchException;
import org.springframework.beans.factory.config.BeanExpressionContext;
import org.springframework.beans.factory.config.BeanExpressionResolver;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.core.MethodParameter;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ValueConstants;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestScope;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import javax.servlet.ServletException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 提供根据名称获取值的通用逻辑，比如根据参数名称获取url上的值，请求上的header值或路径变量等数据
 *
 * Abstract base class for resolving method arguments from a named value.
 * Request parameters, request headers, and path variables are examples of named
 * values. Each may have a name, a required flag, and a default value.
 *
 * <p>Subclasses define how to do the following:
 * <ul>
 * <li>Obtain named value information for a method parameter
 * <li>Resolve names into argument values
 * <li>Handle missing argument values when argument values are required
 * <li>Optionally handle a resolved value
 * </ul>
 *
 * <p>A default value string can contain ${...} placeholders and Spring Expression
 * Language #{...} expressions. For this to work a
 * {@link ConfigurableBeanFactory} must be supplied to the class constructor.
 *
 * <p>A {@link WebDataBinder} is created to apply type conversion to the resolved
 * argument value if it doesn't match the method parameter type.
 *
 * @author Arjen Poutsma
 * @author Rossen Stoyanchev
 * @author Juergen Hoeller
 * @since 3.1
 */
public abstract class AbstractNamedValueMethodArgumentResolver implements HandlerMethodArgumentResolver {

	@Nullable
	private final ConfigurableBeanFactory configurableBeanFactory;

	@Nullable
	private final BeanExpressionContext expressionContext;

	private final Map<MethodParameter, NamedValueInfo> namedValueInfoCache = new ConcurrentHashMap<>(256);


	public AbstractNamedValueMethodArgumentResolver() {
		this.configurableBeanFactory = null;
		this.expressionContext = null;
	}

	/**
	 * Create a new {@link AbstractNamedValueMethodArgumentResolver} instance.
	 * @param beanFactory a bean factory to use for resolving ${...} placeholder
	 * and #{...} SpEL expressions in default values, or {@code null} if default
	 * values are not expected to contain expressions
	 */
	public AbstractNamedValueMethodArgumentResolver(@Nullable ConfigurableBeanFactory beanFactory) {
		this.configurableBeanFactory = beanFactory;
		this.expressionContext =
				(beanFactory != null ? new BeanExpressionContext(beanFactory, new RequestScope()) : null);
	}


	@Override
	@Nullable
	public final Object resolveArgument(MethodParameter parameter, @Nullable ModelAndViewContainer mavContainer,
			NativeWebRequest webRequest, @Nullable WebDataBinderFactory binderFactory) throws Exception {

		// 解析出该参数对应注解的信息：name、require和defaultValue
		NamedValueInfo namedValueInfo = getNamedValueInfo(parameter);

		// Optional参数处理
		MethodParameter nestedParameter = parameter.nestedIfOptional();

		// 注解上的参数名称解析，因为可能包含占位符和表达式，解析结果为空则抛出异常
		Object resolvedName = resolveStringValue(namedValueInfo.name);
		if (resolvedName == null) {
			throw new IllegalArgumentException(
					"Specified name must not resolve to null: [" + namedValueInfo.name + "]");
		}

		// 实际从Request中获取参数
		Object arg = resolveName(resolvedName.toString(), nestedParameter, webRequest);
		if (arg == null) {

			// 参数值为空，但是有默认值的，解析默认值可能存在的占位符和表达式，并返回结果
			if (namedValueInfo.defaultValue != null) {
				arg = resolveStringValue(namedValueInfo.defaultValue);
			}

			// 参数值为空，该参数要求必传，且参数类型不是Optional，则调用handleMissingValue
			// 比如RequestParamMethodArgumentResolver进行了重写，会抛出异常
			else if (namedValueInfo.required && !nestedParameter.isOptional()) {
				handleMissingValue(namedValueInfo.name, nestedParameter, webRequest);
			}

			// 参数值为空，但是不要求必传，则处理一下控制：主要是基础数据类型默认值的处理，只支持布尔类型
			arg = handleNullValue(namedValueInfo.name, arg, nestedParameter.getNestedParameterType());
		}

		// 如果参数值为空字符串，且默认值不为空，解析默认值可能存在的占位符和表达式，并返回结果
		else if ("".equals(arg) && namedValueInfo.defaultValue != null) {
			arg = resolveStringValue(namedValueInfo.defaultValue);
		}

		// todo binderFactory，看起来是WebBinder相关的逻辑，后续解析
		if (binderFactory != null) {
			WebDataBinder binder = binderFactory.createBinder(webRequest, null, namedValueInfo.name);
			try {
				arg = binder.convertIfNecessary(arg, parameter.getParameterType(), parameter);
			}
			catch (ConversionNotSupportedException ex) {
				throw new MethodArgumentConversionNotSupportedException(arg, ex.getRequiredType(),
						namedValueInfo.name, parameter, ex.getCause());
			}
			catch (TypeMismatchException ex) {
				throw new MethodArgumentTypeMismatchException(arg, ex.getRequiredType(),
						namedValueInfo.name, parameter, ex.getCause());
			}
		}

		// 参数值解析完成的后续处理
		handleResolvedValue(arg, namedValueInfo.name, parameter, mavContainer, webRequest);

		return arg;
	}

	/**
	 * Obtain the named value for the given method parameter.
	 */
	private NamedValueInfo getNamedValueInfo(MethodParameter parameter) {

		// 先获取缓存，如果有直接使用缓存
		NamedValueInfo namedValueInfo = this.namedValueInfoCache.get(parameter);

		// 如果缓存为空，则需要创建参数名称和参数值的数据对象
		if (namedValueInfo == null) {

			// 创建参数名称和参数值的数据对象，比如：RequestParamMethodArgumentResolver.createNamedValueInfo，从@RequestParam注解上获取参数名称
			namedValueInfo = createNamedValueInfo(parameter);

			// 根据入参的parameter更新参数信息
			namedValueInfo = updateNamedValueInfo(parameter, namedValueInfo);

			// 将结果放入缓存中
			this.namedValueInfoCache.put(parameter, namedValueInfo);
		}
		return namedValueInfo;
	}

	/**
	 * Create the {@link NamedValueInfo} object for the given method parameter. Implementations typically
	 * retrieve the method annotation by means of {@link MethodParameter#getParameterAnnotation(Class)}.
	 * @param parameter the method parameter
	 * @return the named value information
	 */
	protected abstract NamedValueInfo createNamedValueInfo(MethodParameter parameter);

	/**
	 * Create a new NamedValueInfo based on the given NamedValueInfo with sanitized values.
	 */
	private NamedValueInfo updateNamedValueInfo(MethodParameter parameter, NamedValueInfo info) {
		String name = info.name;

		// 如果info中的参数名称为空，则需要从入参中的MethodParameter上获取参数名称
		if (info.name.isEmpty()) {

			// 获取参数名称，其实就是调用DefaultParameterNameDiscoverer解析方法的参数名称
			name = parameter.getParameterName();

			// 如果无法获取到参数名称，则抛出异常
			if (name == null) {
				throw new IllegalArgumentException(
						"Name for argument type [" + parameter.getNestedParameterType().getName() +
						"] not available, and parameter name information not found in class file either.");
			}
		}
		String defaultValue = (ValueConstants.DEFAULT_NONE.equals(info.defaultValue) ? null : info.defaultValue);
		return new NamedValueInfo(name, info.required, defaultValue);
	}

	/**
	 * Resolve the given annotation-specified value,
	 * potentially containing placeholders and expressions.
	 */
	@Nullable
	private Object resolveStringValue(String value) {
		if (this.configurableBeanFactory == null) {
			return value;
		}
		String placeholdersResolved = this.configurableBeanFactory.resolveEmbeddedValue(value);
		BeanExpressionResolver exprResolver = this.configurableBeanFactory.getBeanExpressionResolver();
		if (exprResolver == null || this.expressionContext == null) {
			return value;
		}
		return exprResolver.evaluate(placeholdersResolved, this.expressionContext);
	}

	/**
	 * Resolve the given parameter type and value name into an argument value.
	 * @param name the name of the value being resolved
	 * @param parameter the method parameter to resolve to an argument value
	 * (pre-nested in case of a {@link java.util.Optional} declaration)
	 * @param request the current request
	 * @return the resolved argument (may be {@code null})
	 * @throws Exception in case of errors
	 */
	@Nullable
	protected abstract Object resolveName(String name, MethodParameter parameter, NativeWebRequest request)
			throws Exception;

	/**
	 * Invoked when a named value is required, but {@link #resolveName(String, MethodParameter, NativeWebRequest)}
	 * returned {@code null} and there is no default value. Subclasses typically throw an exception in this case.
	 * @param name the name for the value
	 * @param parameter the method parameter
	 * @param request the current request
	 * @since 4.3
	 */
	protected void handleMissingValue(String name, MethodParameter parameter, NativeWebRequest request)
			throws Exception {

		handleMissingValue(name, parameter);
	}

	/**
	 * Invoked when a named value is required, but {@link #resolveName(String, MethodParameter, NativeWebRequest)}
	 * returned {@code null} and there is no default value. Subclasses typically throw an exception in this case.
	 * @param name the name for the value
	 * @param parameter the method parameter
	 */
	protected void handleMissingValue(String name, MethodParameter parameter) throws ServletException {
		throw new ServletRequestBindingException("Missing argument '" + name +
				"' for method parameter of type " + parameter.getNestedParameterType().getSimpleName());
	}

	/**
	 * A {@code null} results in a {@code false} value for {@code boolean}s or an exception for other primitives.
	 */
	@Nullable
	private Object handleNullValue(String name, @Nullable Object value, Class<?> paramType) {
		if (value == null) {
			if (Boolean.TYPE.equals(paramType)) {
				return Boolean.FALSE;
			}
			else if (paramType.isPrimitive()) {
				throw new IllegalStateException("Optional " + paramType.getSimpleName() + " parameter '" + name +
						"' is present but cannot be translated into a null value due to being declared as a " +
						"primitive type. Consider declaring it as object wrapper for the corresponding primitive type.");
			}
		}
		return value;
	}

	/**
	 * Invoked after a value is resolved.
	 * @param arg the resolved argument value
	 * @param name the argument name
	 * @param parameter the argument parameter type
	 * @param mavContainer the {@link ModelAndViewContainer} (may be {@code null})
	 * @param webRequest the current request
	 */
	protected void handleResolvedValue(@Nullable Object arg, String name, MethodParameter parameter,
			@Nullable ModelAndViewContainer mavContainer, NativeWebRequest webRequest) {
	}


	/**
	 * Represents the information about a named value, including name, whether it's required and a default value.
	 */
	protected static class NamedValueInfo {

		private final String name;

		private final boolean required;

		@Nullable
		private final String defaultValue;

		public NamedValueInfo(String name, boolean required, @Nullable String defaultValue) {
			this.name = name;
			this.required = required;
			this.defaultValue = defaultValue;
		}
	}

}
