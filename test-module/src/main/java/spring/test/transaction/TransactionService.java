package spring.test.transaction;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 事务服务
 *
 * @author wang.yubin
 * @since 2023/3/7
 */
@Service
public class TransactionService {

	@Transactional(rollbackFor = Exception.class, noRollbackFor = IllegalStateException.class)
	public void test1() {
		System.out.println("test1 method executed");

		throw new IllegalStateException("test1 state illegal");
	}

}
