package spring.test.cycle.depend.async.a;

import org.springframework.stereotype.Component;
import spring.test.cycle.depend.async.B;

import javax.annotation.Resource;

/**
 * @author wang.yubin
 * @since 2023/5/31
 */
@Component
public class A {

	@Resource
	private B b;

}
