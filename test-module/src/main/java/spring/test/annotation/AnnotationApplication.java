package spring.test.annotation;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;

/**
 * @author wang.yubin
 * @since 2023/1/12
 */
@ComponentScan
public class AnnotationApplication {

	public static void main(String[] args) {
		AnnotationConfigApplicationContext context =
				new AnnotationConfigApplicationContext(AnnotationApplication.class);

		AnnotationApplication current = context.getBean(AnnotationApplication.class);

		System.out.println("获取到当前启动类：" + current);
		// System.out.println("：，。、、’；【】《》？：”|{}");
	}

	@Override
	public String toString() {
		return getClass().getName();
	}
}
