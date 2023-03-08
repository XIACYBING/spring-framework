/*
 * Copyright 2002-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.transaction.interceptor;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.lang.Nullable;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * TransactionAttribute implementation that works out whether a given exception
 * should cause transaction rollback by applying a number of rollback rules,
 * both positive and negative. If no rules are relevant to the exception, it
 * behaves like DefaultTransactionAttribute (rolling back on runtime exceptions).
 *
 * <p>{@link TransactionAttributeEditor} creates objects of this class.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @since 09.04.2003
 * @see TransactionAttributeEditor
 */
@SuppressWarnings("serial")
public class RuleBasedTransactionAttribute extends DefaultTransactionAttribute implements Serializable {

	/** Prefix for rollback-on-exception rules in description strings. */
	public static final String PREFIX_ROLLBACK_RULE = "-";

	/** Prefix for commit-on-exception rules in description strings. */
	public static final String PREFIX_COMMIT_RULE = "+";


	/** Static for optimal serializability. */
	private static final Log logger = LogFactory.getLog(RuleBasedTransactionAttribute.class);

	@Nullable
	private List<RollbackRuleAttribute> rollbackRules;


	/**
	 * Create a new RuleBasedTransactionAttribute, with default settings.
	 * Can be modified through bean property setters.
	 * @see #setPropagationBehavior
	 * @see #setIsolationLevel
	 * @see #setTimeout
	 * @see #setReadOnly
	 * @see #setName
	 * @see #setRollbackRules
	 */
	public RuleBasedTransactionAttribute() {
		super();
	}

	/**
	 * Copy constructor. Definition can be modified through bean property setters.
	 * @see #setPropagationBehavior
	 * @see #setIsolationLevel
	 * @see #setTimeout
	 * @see #setReadOnly
	 * @see #setName
	 * @see #setRollbackRules
	 */
	public RuleBasedTransactionAttribute(RuleBasedTransactionAttribute other) {
		super(other);
		this.rollbackRules = (other.rollbackRules != null ? new ArrayList<>(other.rollbackRules) : null);
	}

	/**
	 * Create a new DefaultTransactionAttribute with the given
	 * propagation behavior. Can be modified through bean property setters.
	 * @param propagationBehavior one of the propagation constants in the
	 * TransactionDefinition interface
	 * @param rollbackRules the list of RollbackRuleAttributes to apply
	 * @see #setIsolationLevel
	 * @see #setTimeout
	 * @see #setReadOnly
	 */
	public RuleBasedTransactionAttribute(int propagationBehavior, List<RollbackRuleAttribute> rollbackRules) {
		super(propagationBehavior);
		this.rollbackRules = rollbackRules;
	}


	/**
	 * Set the list of {@code RollbackRuleAttribute} objects
	 * (and/or {@code NoRollbackRuleAttribute} objects) to apply.
	 * @see RollbackRuleAttribute
	 * @see NoRollbackRuleAttribute
	 */
	public void setRollbackRules(List<RollbackRuleAttribute> rollbackRules) {
		this.rollbackRules = rollbackRules;
	}

	/**
	 * Return the list of {@code RollbackRuleAttribute} objects
	 * (never {@code null}).
	 */
	public List<RollbackRuleAttribute> getRollbackRules() {
		if (this.rollbackRules == null) {
			this.rollbackRules = new LinkedList<>();
		}
		return this.rollbackRules;
	}


	/**
	 * Winning rule is the shallowest rule (that is, the closest in the
	 * inheritance hierarchy to the exception). If no rule applies (-1),
	 * return false.
	 * @see TransactionAttribute#rollbackOn(java.lang.Throwable)
	 */
	@Override
	public boolean rollbackOn(Throwable ex) {
		if (logger.isTraceEnabled()) {
			logger.trace("Applying rules to determine whether transaction should rollback on " + ex);
		}

		RollbackRuleAttribute winner = null;
		int deepest = Integer.MAX_VALUE;

		if (this.rollbackRules != null) {

			// rollbackRules的顺序会影响到匹配的结果，比如BException和CException都继承了AException，
			// 我在某个事务注解上指定@Transactional(rollbackFor = BException.class, noRollbackFor = CException.class)
			// 此时如果rollbackFor的规则先于noRollbackFor，那么事务就会回滚，反之就不会回滚
			// 从{@link #setRollbackRules}方法向上追踪，发现几处调用方，都是先处理rollbackFor，再处理noRollbackFor，所以在上述场景中，最终结果是回滚
			for (RollbackRuleAttribute rule : this.rollbackRules) {

				// depth字段代表抛出的异常在规则内的第几层，因为回滚异常和不会滚异常的匹配上，都会考虑到继承的异常，比如抛出的是RuntimeException，
				// 指定回滚的异常是Exception，此时返回的depth是1，代表深度是2
				int depth = rule.getDepth(ex);

				// depth >= 0代表匹配到，还有可能返回-1，代表未匹配到
				// depth < deepest，代表当前异常在当前规则中匹配到的深度，比之前匹配到的最小深度还要小，即优先级更高，更应该被应用
				if (depth >= 0 && depth < deepest) {

					// 将当前深度赋值给deepest，以便后续的规则匹配
					deepest = depth;

					// 设置当前规则为winner
					winner = rule;
				}
			}
		}

		if (logger.isTraceEnabled()) {
			logger.trace("Winning rollback rule is: " + winner);
		}

		// 如果没有匹配到规则，则调用父级的rollbackOn：判断抛出的异常是否RuntimeException或Error
		// User superclass behavior (rollback on unchecked) if no rule matches.
		if (winner == null) {
			logger.trace("No relevant rollback rule found: applying default rules");
			return super.rollbackOn(ex);
		}

		// 不回滚规则会映射为NoRollbackRuleAttribute
		// 如果winner是NoRollbackRuleAttribute，此处会返回false，代表不回滚，否则就回滚
		return !(winner instanceof NoRollbackRuleAttribute);
	}


	@Override
	public String toString() {
		StringBuilder result = getAttributeDescription();
		if (this.rollbackRules != null) {
			for (RollbackRuleAttribute rule : this.rollbackRules) {
				String sign = (rule instanceof NoRollbackRuleAttribute ? PREFIX_COMMIT_RULE : PREFIX_ROLLBACK_RULE);
				result.append(',').append(sign).append(rule.getExceptionName());
			}
		}
		return result.toString();
	}

}
