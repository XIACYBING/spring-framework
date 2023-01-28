package spring.test.cycle.depend;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;

/**
 * 循环依赖应用
 *
 * @author wang.yubin
 * @since 2023/1/27
 */
@ComponentScan
public class CycleDependApplication {

	public static void main(String[] args) {
		new AnnotationConfigApplicationContext(CycleDependApplication.class);
	}

}
