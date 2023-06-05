package spring.test.constructor.inject;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;

/**
 * 构造器注入，确认构造器注入流程的相关逻辑，
 * 主要的逻辑在{@link org.springframework.beans.factory.support.ConstructorResolver#autowireConstructor}中
 *
 * @author wang.yubin
 * @since 2023/6/5
 */
@ComponentScan
public class ConstructorInjectApplication {

	public static void main(String[] args) {
		new AnnotationConfigApplicationContext(ConstructorInjectApplication.class);
	}

}
