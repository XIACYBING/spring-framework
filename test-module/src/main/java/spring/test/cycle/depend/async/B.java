package spring.test.cycle.depend.async;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import spring.test.cycle.depend.async.a.A;

import javax.annotation.Resource;

/**
 * @author wang.yubin
 * @since 2023/5/31
 */
@Component
public class B {

	@Resource
	private A a;

	@Async
	public void async(){
		System.out.println(Thread.currentThread().getName());
	}

}
