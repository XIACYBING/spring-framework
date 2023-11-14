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

package org.springframework.cache.annotation;

import org.springframework.aop.support.AbstractBeanFactoryPointcutAdvisor;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.cache.config.CacheManagementConfigUtils;
import org.springframework.cache.interceptor.BeanFactoryCacheOperationSourceAdvisor;
import org.springframework.cache.interceptor.CacheInterceptor;
import org.springframework.cache.interceptor.CacheOperationSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;

/**
 * {@code @Configuration} class that registers the Spring infrastructure beans necessary
 * to enable proxy-based annotation-driven cache management.
 * <p>
 * 通过AOP判断需要进行缓存代理的类，并将{@link #cacheInterceptor()}织入代理类中
 *
 * @author Chris Beams
 * @author Juergen Hoeller
 * @see EnableCaching
 * @see CachingConfigurationSelector
 * @since 3.1
 */
@Configuration
@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
public class ProxyCachingConfiguration extends AbstractCachingConfiguration {

	/**
	 * 缓存操作的Advisor，通过{@link BeanFactoryCacheOperationSourceAdvisor#getPointcut()}判断一个类/方法是否要进行缓存代理，如果要，则获取
	 * {@link AbstractBeanFactoryPointcutAdvisor#getAdvice()}进行代理
	 *
	 * @see AopUtils#canApply(org.springframework.aop.Advisor, java.lang.Class, boolean)
	 */
	@Bean(name = CacheManagementConfigUtils.CACHE_ADVISOR_BEAN_NAME)
	@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
	public BeanFactoryCacheOperationSourceAdvisor cacheAdvisor() {
		BeanFactoryCacheOperationSourceAdvisor advisor = new BeanFactoryCacheOperationSourceAdvisor();
		advisor.setCacheOperationSource(cacheOperationSource());
		advisor.setAdvice(cacheInterceptor());
		if (this.enableCaching != null) {
			advisor.setOrder(this.enableCaching.<Integer>getNumber("order"));
		}
		return advisor;
	}

	@Bean
	@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
	public CacheOperationSource cacheOperationSource() {
		return new AnnotationCacheOperationSource();
	}

	/**
	 * 实际进行缓存操作的Advice，拦截器
	 */
	@Bean
	@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
	public CacheInterceptor cacheInterceptor() {
		CacheInterceptor interceptor = new CacheInterceptor();
		interceptor.configure(this.errorHandler, this.keyGenerator, this.cacheResolver, this.cacheManager);
		interceptor.setCacheOperationSource(cacheOperationSource());
		return interceptor;
	}

}
