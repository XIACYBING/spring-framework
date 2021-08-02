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

package org.springframework.aop.aspectj.annotation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.aspectj.lang.reflect.PerClauseKind;

import org.springframework.aop.Advisor;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * Helper for retrieving @AspectJ beans from a BeanFactory and building
 * Spring Advisors based on them, for use with auto-proxying.
 *
 * @author Juergen Hoeller
 * @since 2.0.2
 * @see AnnotationAwareAspectJAutoProxyCreator
 */
public class BeanFactoryAspectJAdvisorsBuilder {

	private final ListableBeanFactory beanFactory;

	private final AspectJAdvisorFactory advisorFactory;

	@Nullable
	private volatile List<String> aspectBeanNames;

	private final Map<String, List<Advisor>> advisorsCache = new ConcurrentHashMap<>();

	private final Map<String, MetadataAwareAspectInstanceFactory> aspectFactoryCache = new ConcurrentHashMap<>();


	/**
	 * Create a new BeanFactoryAspectJAdvisorsBuilder for the given BeanFactory.
	 * @param beanFactory the ListableBeanFactory to scan
	 */
	public BeanFactoryAspectJAdvisorsBuilder(ListableBeanFactory beanFactory) {
		this(beanFactory, new ReflectiveAspectJAdvisorFactory(beanFactory));
	}

	/**
	 * Create a new BeanFactoryAspectJAdvisorsBuilder for the given BeanFactory.
	 * @param beanFactory the ListableBeanFactory to scan
	 * @param advisorFactory the AspectJAdvisorFactory to build each Advisor with
	 */
	public BeanFactoryAspectJAdvisorsBuilder(ListableBeanFactory beanFactory, AspectJAdvisorFactory advisorFactory) {
		Assert.notNull(beanFactory, "ListableBeanFactory must not be null");
		Assert.notNull(advisorFactory, "AspectJAdvisorFactory must not be null");
		this.beanFactory = beanFactory;
		this.advisorFactory = advisorFactory;
	}


	/**
	 * Look for AspectJ-annotated aspect beans in the current bean factory,
	 * and return to a list of Spring AOP Advisors representing them.
	 * <p>Creates a Spring Advisor for each AspectJ advice method.
	 * @return the list of {@link org.springframework.aop.Advisor} beans
	 * @see #isEligibleBean
	 */
	public List<Advisor> buildAspectJAdvisors() {
		List<String> aspectNames = this.aspectBeanNames;

		if (aspectNames == null) {
			synchronized (this) {

				// 加锁，二次检查aspectJ相关的beanName不为空
				aspectNames = this.aspectBeanNames;

				// 如果为空，此处就需要开始获取所有有效的aspectJAdvisorName
				if (aspectNames == null) {
					List<Advisor> advisors = new ArrayList<>();
					aspectNames = new ArrayList<>();

					// 获取当前beanFactory中所有beanName，包含非单例，但不包含未初始化完全的bean
					String[] beanNames = BeanFactoryUtils.beanNamesForTypeIncludingAncestors(
							this.beanFactory, Object.class, true, false);
					for (String beanName : beanNames) {
						// 判断是否有效的增强器，底层实现只有一种
						// org.springframework.aop.aspectj.annotation.AnnotationAwareAspectJAutoProxyCreator.isEligibleAspectBean
						// 判断bean名称是否符合某种模式，或者匹配模式为空
						if (!isEligibleBean(beanName)) {
							continue;
						}
						// We must be careful not to instantiate beans eagerly as in this case they
						// would be cached by the Spring container but would not have been weaved.
						// 忽略Class为空的bean
						Class<?> beanType = this.beanFactory.getType(beanName);
						if (beanType == null) {
							continue;
						}

						// 判断当前bean的Class是否是一个aspect相关的Class，其实就是判断是否包含Aspect注解且Class中的DeclareField中没有包含有ajc编辑的字段
						// 具体判断在org.springframework.aop.aspectj.annotation.AbstractAspectJAdvisorFactory.isAspect
						if (this.advisorFactory.isAspect(beanType)) {
							// 是的话则将对应beanName加入aspectNames集合中
							aspectNames.add(beanName);

							// 并根据bean的name和Class构建一个Aspect元信息数据实体
							AspectMetadata amd = new AspectMetadata(beanType, beanName);
							// 可以理解为如果当前Aspect有单例属性？
							if (amd.getAjType().getPerClause().getKind() == PerClauseKind.SINGLETON) {

								// 构建一个aspect实例工厂
								MetadataAwareAspectInstanceFactory factory =
										new BeanFactoryAspectInstanceFactory(this.beanFactory, beanName);

								// 并从当前工厂中将将这个aspect中的增强器全部提取出来
								List<Advisor> classAdvisors = this.advisorFactory.getAdvisors(factory);
								// 如果当前beanName对应的bean是单例，则直接将这个beanName对应的方法增强器放到增强器缓存集合advisorsCache中
								if (this.beanFactory.isSingleton(beanName)) {
									this.advisorsCache.put(beanName, classAdvisors);
								}
								else {
									// 否则将beanName和对应aspect实例工厂放入aspectFactoryCache中，方便每次需要增强实例时取用
									this.aspectFactoryCache.put(beanName, factory);
								}
								// 然后将获取到的classAdvisors加入到增强器集合advisors中
								advisors.addAll(classAdvisors);
							}
							else {
								// Per target or per this.
								// 如果对应Aspect没有某个单例实行，而aspect所属的bean又是个单例，则抛出异常
								if (this.beanFactory.isSingleton(beanName)) {
									throw new IllegalArgumentException("Bean with name '" + beanName +
											"' is a singleton, but aspect instantiation model is not singleton");
								}
								// 否则说明对应bean非单例，配置一个prototype的aspect实例化工厂，并加入到aspectFactoryCache缓存中，方便每次需要增强实例时取用
								MetadataAwareAspectInstanceFactory factory =
										new PrototypeAspectInstanceFactory(this.beanFactory, beanName);
								this.aspectFactoryCache.put(beanName, factory);

								// 并获取当前工厂中所有能获取到的增强器，加入到增强器集合中
								advisors.addAll(this.advisorFactory.getAdvisors(factory));
							}
						}
					}

					// 循环结束后，将所有aspect相关的beanName，赋值给当前的aspectBeanNames，供下一次使用
					this.aspectBeanNames = aspectNames;
					return advisors;
				}
			}
		}

		// 如果aspect不为空，但是集合内容为空，说明执行过上面的逻辑一次，且没有获取到任何aspect的beanName，直接返回个空集合即可
		if (aspectNames.isEmpty()) {
			return Collections.emptyList();
		}
		List<Advisor> advisors = new ArrayList<>();
		// 循环aspectNames，从两层缓存中获取所有增强器
		for (String aspectName : aspectNames) {
			// 从单例增强器缓存中获取增强器
			List<Advisor> cachedAdvisors = this.advisorsCache.get(aspectName);
			if (cachedAdvisors != null) {
				advisors.addAll(cachedAdvisors);
			}
			else {
				// 从aspect实例化工厂中获取aspect实例化工厂，再获取该工厂的所有增强器加入增强器集合中
				MetadataAwareAspectInstanceFactory factory = this.aspectFactoryCache.get(aspectName);
				advisors.addAll(this.advisorFactory.getAdvisors(factory));
			}
		}
		// 返回获取到的所有增强器
		return advisors;
	}

	/**
	 * Return whether the aspect bean with the given name is eligible.
	 * @param beanName the name of the aspect bean
	 * @return whether the bean is eligible
	 */
	protected boolean isEligibleBean(String beanName) {
		return true;
	}

}
