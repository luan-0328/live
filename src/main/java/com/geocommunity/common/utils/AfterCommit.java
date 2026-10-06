package com.geocommunity.common.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 外部副作用仅在数据库提交成功后执行；回滚时不执行。 */
public final class AfterCommit {
    private static final Logger log = LoggerFactory.getLogger(AfterCommit.class);
    private AfterCommit() {}

    public static void run(Runnable action) {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { safely(action); }
            });
        } else {
            safely(action);
        }
    }

    private static void safely(Runnable action) {
        try { action.run(); }
        catch (Exception e) { log.error("提交后的缓存/会话操作失败", e); }
    }
}
