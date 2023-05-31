package spring.test.cycle.depend.async;

import org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.AsyncAnnotationBeanPostProcessor;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * {@link org.springframework.scheduling.annotation.Async}导致的循环依赖异常
 *
 * Exception encountered during context initialization - cancelling refresh attempt: org.springframework.beans.factory.BeanCurrentlyInCreationException: Error creating bean with name 'b': Bean with name 'b' has been injected into other beans [a] in its raw version as part of a circular reference, but has eventually been wrapped. This means that said other beans do not use the final version of the bean. This is often the result of over-eager type matching - consider using 'getBeanNamesForType' with the 'allowEagerInit' flag turned off, for example.
 * Exception in thread "main" org.springframework.beans.factory.BeanCurrentlyInCreationException: Error creating bean with name 'b': Bean with name 'b' has been injected into other beans [a] in its raw version as part of a circular reference, but has eventually been wrapped. This means that said other beans do not use the final version of the bean. This is often the result of over-eager type matching - consider using 'getBeanNamesForType' with the 'allowEagerInit' flag turned off, for example.
 * 	at org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory.doCreateBean(AbstractAutowireCapableBeanFactory.java:668)
 * 	at org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory.createBean(AbstractAutowireCapableBeanFactory.java:520)
 *
 * 核心原因在于{@link AsyncAnnotationBeanPostProcessor}在{@code initializeBean}中为{@code exposedObject}生成代理并返回，从而导致进入下层的
 * {@code hasDependentBean}的检查流程，最终发现循环依赖并报错
 *
 * 而对于事务等其他的处理，则是在{@code earlySingletonExposure}的判断流程中，从{@code getSingleton}中获取到已代理过的对象，并替换{@code exposedObject}
 *
 * 以上描述基于{@link AbstractAutowireCapableBeanFactory#doCreateBean}
 *
 * @author wang.yubin
 * @since 2023/5/31
 */
@EnableAsync
@ComponentScan
public class AsyncCircleDependApplication {

	public static void main(String[] args) {
		new AnnotationConfigApplicationContext(AsyncCircleDependApplication.class);
	}

}
