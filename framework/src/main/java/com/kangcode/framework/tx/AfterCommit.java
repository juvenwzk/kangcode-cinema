package com.kangcode.framework.tx;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 事务提交后再执行。
 * <p>用于「DB 改成功才发 MQ」：避免事务回滚了、消息却已经发出去。
 */
public final class AfterCommit {

    private AfterCommit() {
    }

    public static void run(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            // 有事务：挂个回调，等 commit 成功后执行
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            // 没有事务上下文（比如在 MQ 消费线程里直接调用）→ 直接执行
            action.run();
        }
    }
}