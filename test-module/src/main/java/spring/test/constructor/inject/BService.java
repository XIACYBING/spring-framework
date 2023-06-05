package spring.test.constructor.inject;

import org.springframework.stereotype.Component;

/**
 * @author wang.yubin
 * @since 2023/6/5
 */
@Component
public class BService {

	private final AService aService;

	public BService(AService aService) {
		this.aService = aService;
		System.out.println("AService注入完成");
	}

}
