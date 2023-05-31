package spring.test.autowired;

import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

/**
 * 自动注入测试
 *
 * @author wang.yubin
 * @since 2023/5/25
 */
@ComponentScan
public class AutowireModeApplication {

	public static void main(String[] args) {
		AnnotationConfigApplicationContext context =
				new AnnotationConfigApplicationContext(AutowireModeApplication.class);

		AbstractBeanDefinition catBd = (AbstractBeanDefinition)context.getBeanDefinition("autowireModeApplication.Cat");
		AbstractBeanDefinition dogBd = (AbstractBeanDefinition)context.getBeanDefinition("dog");
		AbstractBeanDefinition configBeanBd = (AbstractBeanDefinition)context.getBeanDefinition("autowireModeApplication.ConfigBean");
		System.out.println("cat：" + catBd.getAutowireMode());
		System.out.println("dog：" + dogBd.getAutowireMode());
		System.out.println("configBean：" + configBeanBd.getAutowireMode());
	}

	@Configuration
	public static class ConfigBean {
		@Bean
		public Dog dog() {
			return new Dog();
		}
	}

	@Component
	public static class Cat {}

	public static class Dog {}

}
