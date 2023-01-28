package spring.test.cycle.depend;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * 第三个服务
 *
 * @author wang.yubin
 * @since 2023/1/28
 */
@Service
@DependsOn("firstService")
@SuppressWarnings("unused")
public class ThirdService {

	@Autowired
	public ThirdService(FirstService firstService) {
		this.firstService = firstService;
	}

	@SuppressWarnings("FieldCanBeLocal")
	private final FirstService firstService;

	@Autowired
	private FirstService autowiredFirstService;

	@Resource
	private FirstService resourceFirstService;

	@Value("${valueOne:1}")
	private String valueOne;

	@Value("${valueTwo:2}")
	private int valueTwo;

	@Value("${valueThreeList:3,4,5}")
	private String valueThreeList;

}
