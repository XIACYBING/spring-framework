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

package org.springframework.aop.framework;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;

import org.springframework.aop.SpringProxy;
import org.springframework.aop.TargetClassAware;
import org.springframework.aop.TargetSource;
import org.springframework.aop.support.AopUtils;
import org.springframework.aop.target.SingletonTargetSource;
import org.springframework.core.DecoratingProxy;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;

/**
 * Utility methods for AOP proxy factories.
 * Mainly for internal use within the AOP framework.
 *
 * <p>See {@link org.springframework.aop.support.AopUtils} for a collection of
 * generic AOP utility methods which do not depend on AOP framework internals.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @see org.springframework.aop.support.AopUtils
 */
public abstract class AopProxyUtils {

	/**
	 * Obtain the singleton target object behind the given proxy, if any.
	 * @param candidate the (potential) proxy to check
	 * @return the singleton target object managed in a {@link SingletonTargetSource},
	 * or {@code null} in any other case (not a proxy, not an existing singleton target)
	 * @since 4.3.8
	 * @see Advised#getTargetSource()
	 * @see SingletonTargetSource#getTarget()
	 */
	@Nullable
	public static Object getSingletonTarget(Object candidate) {
		if (candidate instanceof Advised) {
			TargetSource targetSource = ((Advised) candidate).getTargetSource();
			if (targetSource instanceof SingletonTargetSource) {
				return ((SingletonTargetSource) targetSource).getTarget();
			}
		}
		return null;
	}

	/**
	 * Determine the ultimate target class of the given bean instance, traversing
	 * not only a top-level proxy but any number of nested proxies as well &mdash;
	 * as long as possible without side effects, that is, just for singleton targets.
	 * @param candidate the instance to check (might be an AOP proxy)
	 * @return the ultimate target class (or the plain class of the given
	 * object as fallback; never {@code null})
	 * @see org.springframework.aop.TargetClassAware#getTargetClass()
	 * @see Advised#getTargetSource()
	 */
	public static Class<?> ultimateTargetClass(Object candidate) {
		Assert.notNull(candidate, "Candidate object must not be null");
		Object current = candidate;
		Class<?> result = null;
		while (current instanceof TargetClassAware) {
			result = ((TargetClassAware) current).getTargetClass();
			current = getSingletonTarget(current);
		}
		if (result == null) {
			result = (AopUtils.isCglibProxy(candidate) ? candidate.getClass().getSuperclass() : candidate.getClass());
		}
		return result;
	}

	/**
	 * Determine the complete set of interfaces to proxy for the given AOP configuration.
	 * <p>This will always add the {@link Advised} interface unless the AdvisedSupport's
	 * {@link AdvisedSupport#setOpaque "opaque"} flag is on. Always adds the
	 * {@link org.springframework.aop.SpringProxy} marker interface.
	 * @param advised the proxy config
	 * @return the complete set of interfaces to proxy
	 * @see SpringProxy
	 * @see Advised
	 */
	public static Class<?>[] completeProxiedInterfaces(AdvisedSupport advised) {
		return completeProxiedInterfaces(advised, false);
	}

	/**
	 * Determine the complete set of interfaces to proxy for the given AOP configuration.
	 * <p>This will always add the {@link Advised} interface unless the AdvisedSupport's
	 * {@link AdvisedSupport#setOpaque "opaque"} flag is on. Always adds the
	 * {@link org.springframework.aop.SpringProxy} marker interface.
	 * @param advised the proxy config
	 * @param decoratingProxy whether to expose the {@link DecoratingProxy} interface
	 * @return the complete set of interfaces to proxy
	 * @since 4.3
	 * @see SpringProxy
	 * @see Advised
	 * @see DecoratingProxy
	 */
	static Class<?>[] completeProxiedInterfaces(AdvisedSupport advised, boolean decoratingProxy) {
		Class<?>[] specifiedInterfaces = advised.getProxiedInterfaces();
		if (specifiedInterfaces.length == 0) {
			// No user-specified interfaces: check whether target class is an interface.
			Class<?> targetClass = advised.getTargetClass();
			if (targetClass != null) {
				if (targetClass.isInterface()) {
					advised.setInterfaces(targetClass);
				}
				else if (Proxy.isProxyClass(targetClass)) {
					advised.setInterfaces(targetClass.getInterfaces());
				}
				specifiedInterfaces = advised.getProxiedInterfaces();
			}
		}
		boolean addSpringProxy = !advised.isInterfaceProxied(SpringProxy.class);
		boolean addAdvised = !advised.isOpaque() && !advised.isInterfaceProxied(Advised.class);
		boolean addDecoratingProxy = (decoratingProxy && !advised.isInterfaceProxied(DecoratingProxy.class));
		int nonUserIfcCount = 0;
		if (addSpringProxy) {
			nonUserIfcCount++;
		}
		if (addAdvised) {
			nonUserIfcCount++;
		}
		if (addDecoratingProxy) {
			nonUserIfcCount++;
		}
		Class<?>[] proxiedInterfaces = new Class<?>[specifiedInterfaces.length + nonUserIfcCount];
		System.arraycopy(specifiedInterfaces, 0, proxiedInterfaces, 0, specifiedInterfaces.length);
		int index = specifiedInterfaces.length;
		if (addSpringProxy) {
			proxiedInterfaces[index] = SpringProxy.class;
			index++;
		}
		if (addAdvised) {
			proxiedInterfaces[index] = Advised.class;
			index++;
		}
		if (addDecoratingProxy) {
			proxiedInterfaces[index] = DecoratingProxy.class;
		}
		return proxiedInterfaces;
	}

	/**
	 * Extract the user-specified interfaces that the given proxy implements,
	 * i.e. all non-Advised interfaces that the proxy implements.
	 * @param proxy the proxy to analyze (usually a JDK dynamic proxy)
	 * @return all user-specified interfaces that the proxy implements,
	 * in the original order (never {@code null} or empty)
	 * @see Advised
	 */
	public static Class<?>[] proxiedUserInterfaces(Object proxy) {
		Class<?>[] proxyInterfaces = proxy.getClass().getInterfaces();
		int nonUserIfcCount = 0;
		if (proxy instanceof SpringProxy) {
			nonUserIfcCount++;
		}
		if (proxy instanceof Advised) {
			nonUserIfcCount++;
		}
		if (proxy instanceof DecoratingProxy) {
			nonUserIfcCount++;
		}
		Class<?>[] userInterfaces = Arrays.copyOf(proxyInterfaces, proxyInterfaces.length - nonUserIfcCount);
		Assert.notEmpty(userInterfaces, "JDK proxy must implement one or more interfaces");
		return userInterfaces;
	}

	/**
	 * Check equality of the proxies behind the given AdvisedSupport objects.
	 * Not the same as equality of the AdvisedSupport objects:
	 * rather, equality of interfaces, advisors and target sources.
	 */
	public static boolean equalsInProxy(AdvisedSupport a, AdvisedSupport b) {
		return (a == b ||
				(equalsProxiedInterfaces(a, b) && equalsAdvisors(a, b) && a.getTargetSource().equals(b.getTargetSource())));
	}

	/**
	 * Check equality of the proxied interfaces behind the given AdvisedSupport objects.
	 */
	public static boolean equalsProxiedInterfaces(AdvisedSupport a, AdvisedSupport b) {
		return Arrays.equals(a.getProxiedInterfaces(), b.getProxiedInterfaces());
	}

	/**
	 * Check equality of the advisors behind the given AdvisedSupport objects.
	 */
	public static boolean equalsAdvisors(AdvisedSupport a, AdvisedSupport b) {
		return Arrays.equals(a.getAdvisors(), b.getAdvisors());
	}


	/**
	 * Adapt the given arguments to the target signature in the given method,
	 * if necessary: in particular, if a given vararg argument array does not
	 * match the array type of the declared vararg parameter in the method.
	 * @param method the target method
	 * @param arguments the given arguments
	 * @return a cloned argument array, or the original if no adaptation is needed
	 * @since 4.2.3
	 */
	static Object[] adaptArgumentsIfNecessary(Method method, @Nullable Object[] arguments) {
		if (ObjectUtils.isEmpty(arguments)) {
			return new Object[0];
		}
		// 对于可变参数的一些适配处理
		if (method.isVarArgs()) {
			if (method.getParameterCount() == arguments.length) {
				// 获取参数类型集合
				Class<?>[] paramTypes = method.getParameterTypes();
				// 获取最后一个参数的索引
				int varargIndex = paramTypes.length - 1;
				// 获取最后一个参数的类型
				// todo 在可变参数中，方法的最后一个参数类型是什么？以及可变参数在方法参数集合中会以什么类型展示？
				Class<?> varargType = paramTypes[varargIndex];
				// 如果类型是数组
				if (varargType.isArray()) {
					// 获取最后一个参数(数组)
					Object varargArray = arguments[varargIndex];
					// 如果最后一个参数是Object数组，且非最后一个可变参数类型的实例
					if (varargArray instanceof Object[] && !varargType.isInstance(varargArray)) {
						// 新建一个参数数组，从原参数数组上复制，除了最后一个参数
						Object[] newArguments = new Object[arguments.length];
						System.arraycopy(arguments, 0, newArguments, 0, varargIndex);
						// 获取可变类型中实际的元素类型
						Class<?> targetElementType = varargType.getComponentType();
						int varargLength = Array.getLength(varargArray);
						// 新建一个可变参数类型的数组，并将原来的可变参数内容复制进去
						Object newVarargArray = Array.newInstance(targetElementType, varargLength);
						System.arraycopy(varargArray, 0, newVarargArray, 0, varargLength);
						// 将可变参数设置到新参数数组中的最后一位，并返回新参数数组
						newArguments[varargIndex] = newVarargArray;
						return newArguments;
					}
				}
			}
		}
		// 无可变参数直接返回
		return arguments;
	}

}
