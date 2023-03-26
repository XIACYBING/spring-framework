/*
 * Copyright 2002-2020 the original author or authors.
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

package org.springframework.validation.beanvalidation;

import java.lang.reflect.Method;
import java.util.Set;

import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import javax.validation.executable.ExecutableValidator;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.SmartFactoryBean;
import org.springframework.core.BridgeMethodResolver;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.ClassUtils;
import org.springframework.validation.annotation.Validated;

/**
 * An AOP Alliance {@link MethodInterceptor} implementation that delegates to a
 * JSR-303 provider for performing method-level validation on annotated methods.
 *
 * <p>Applicable methods have JSR-303 constraint annotations on their parameters
 * and/or on their return value (in the latter case specified at the method level,
 * typically as inline annotation).
 *
 * <p>E.g.: {@code public @NotNull Object myValidMethod(@NotNull String arg1, @Max(10) int arg2)}
 *
 * <p>Validation groups can be specified through Spring's {@link Validated} annotation
 * at the type level of the containing target class, applying to all public service methods
 * of that class. By default, JSR-303 will validate against its default group only.
 *
 * <p>As of Spring 5.0, this functionality requires a Bean Validation 1.1+ provider.
 *
 * @author Juergen Hoeller
 * @since 3.1
 * @see MethodValidationPostProcessor
 * @see javax.validation.executable.ExecutableValidator
 */
public class MethodValidationInterceptor implements MethodInterceptor {

	private final Validator validator;


	/**
	 * Create a new MethodValidationInterceptor using a default JSR-303 validator underneath.
	 */
	public MethodValidationInterceptor() {

		// 使用默认的validator
		this(Validation.buildDefaultValidatorFactory());
	}

	/**
	 * Create a new MethodValidationInterceptor using the given JSR-303 ValidatorFactory.
	 * @param validatorFactory the JSR-303 ValidatorFactory to use
	 */
	public MethodValidationInterceptor(ValidatorFactory validatorFactory) {
		this(validatorFactory.getValidator());
	}

	/**
	 * Create a new MethodValidationInterceptor using the given JSR-303 Validator.
	 * @param validator the JSR-303 Validator to use
	 */
	public MethodValidationInterceptor(Validator validator) {
		this.validator = validator;
	}


	@Override
	public Object invoke(MethodInvocation invocation) throws Throwable {

		// 避免对FactoryBean.getObjectType/isSingleton进行校验
		// Avoid Validator invocation on FactoryBean.getObjectType/isSingleton
		if (isFactoryBeanMetadataMethod(invocation.getMethod())) {
			return invocation.proceed();
		}

		// 确认当前要校验的group类型数组
		Class<?>[] groups = determineValidationGroups(invocation);

		// 获取可执行校验器，可用来校验方法执行情况
		// Standard Bean Validation 1.1 API
		ExecutableValidator execVal = this.validator.forExecutables();

		// 获取当前需要执行的方法
		Method methodToValidate = invocation.getMethod();

		// 新建违反限制的集合
		Set<ConstraintViolation<Object>> result;

		try {

			// 校验方法参数
			result = execVal.validateParameters(
					invocation.getThis(), methodToValidate, invocation.getArguments(), groups);
		}
		catch (IllegalArgumentException ex) {

			// 可能存在接口泛型和实际实现类型的一些差异，需要获取桥接方法/bridged method，再次执行
			// Probably a generic type mismatch between interface and impl as reported in SPR-12237 / HV-1011
			// Let's try to find the bridged method on the implementation class...
			methodToValidate = BridgeMethodResolver.findBridgedMethod(
					ClassUtils.getMostSpecificMethod(invocation.getMethod(), invocation.getThis().getClass()));

			//  再次校验入参
			result = execVal.validateParameters(
					invocation.getThis(), methodToValidate, invocation.getArguments(), groups);
		}

		// 入参校验存在异常，直接抛出异常
		if (!result.isEmpty()) {
			throw new ConstraintViolationException(result);
		}

		// 如果没有异常，直接执行方法
		Object returnValue = invocation.proceed();

		// 校验方法返回值，如果有异常，则抛出
		result = execVal.validateReturnValue(invocation.getThis(), methodToValidate, returnValue, groups);
		if (!result.isEmpty()) {
			throw new ConstraintViolationException(result);
		}

		return returnValue;
	}

	private boolean isFactoryBeanMetadataMethod(Method method) {

		// 获取方法的声明类
		Class<?> clazz = method.getDeclaringClass();

		// 如果声明类是接口
		// Call from interface-based proxy handle, allowing for an efficient check?
		if (clazz.isInterface()) {

			// 声明类是FactoryBean或SmartFactoryBean，且方法非getObject，则返回true
			return ((clazz == FactoryBean.class || clazz == SmartFactoryBean.class) &&
					!method.getName().equals("getObject"));
		}

		// 判断声明类是否FactoryBean或SmartFactoryBean的实现类
		// Call from CGLIB proxy handle, potentially implementing a FactoryBean method?
		Class<?> factoryBeanType = null;
		if (SmartFactoryBean.class.isAssignableFrom(clazz)) {
			factoryBeanType = SmartFactoryBean.class;
		}
		else if (FactoryBean.class.isAssignableFrom(clazz)) {
			factoryBeanType = FactoryBean.class;
		}

		// 声明类是FactoryBean或SmartFactoryBean的实现类，且方法非getObject，且方法也在FactoryBean或SmartFactoryBean中声明了，则返回true
		return (factoryBeanType != null && !method.getName().equals("getObject") &&
				ClassUtils.hasMethod(factoryBeanType, method));
	}

	/**
	 * Determine the validation groups to validate against for the given method invocation.
	 * <p>Default are the validation groups as specified in the {@link Validated} annotation
	 * on the containing target class of the method.
	 * @param invocation the current MethodInvocation
	 * @return the applicable validation groups as a Class array
	 */
	protected Class<?>[] determineValidationGroups(MethodInvocation invocation) {

		// 获取方法上的注解信息，为空则获取类上的注解信息
		Validated validatedAnn = AnnotationUtils.findAnnotation(invocation.getMethod(), Validated.class);
		if (validatedAnn == null) {
			validatedAnn = AnnotationUtils.findAnnotation(invocation.getThis().getClass(), Validated.class);
		}

		// 获取注解上的校验分组
		return (validatedAnn != null ? validatedAnn.value() : new Class<?>[0]);
	}

}
