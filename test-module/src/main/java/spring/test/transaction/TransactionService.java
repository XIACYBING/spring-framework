package spring.test.transaction;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

/**
 * 事务服务
 *
 * @author wang.yubin
 * @since 2023/3/7
 */
@Service
public class TransactionService {

	@Resource
	private TransactionService transactionService;

	@Transactional(rollbackFor = Exception.class, noRollbackFor = IllegalStateException.class)
	public void test1() {
		System.out.println("test1 method executed");

		throw new IllegalStateException("test1 state illegal");
	}

	@Transactional(rollbackFor = Exception.class)
	public void test2() {
		System.out.println("test2 method executed");

		transactionService.neverTransaction();
	}

	@Transactional(rollbackFor = Exception.class, propagation = Propagation.NEVER)
	public void neverTransaction() {
		System.out.println("neverTransaction Method executed");
	}

}
