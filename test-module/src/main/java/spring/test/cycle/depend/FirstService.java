package spring.test.cycle.depend;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * 第一个服务
 *
 * @author wang.yubin
 * @since 2023/1/27
 */
@Service
public class FirstService {

	@Autowired
	@Qualifier("secondService")
	private SecondService secondService;
}
