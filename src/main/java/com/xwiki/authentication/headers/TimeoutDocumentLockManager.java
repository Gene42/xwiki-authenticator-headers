package com.xwiki.authentication.headers;

import org.xwiki.model.reference.DocumentReference;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.StampedLock;

import javax.annotation.Nonnull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DESCRIPTION.
 *
 * @version $Id$
 */
public class TimeoutDocumentLockManager {

    private Logger logger = LoggerFactory.getLogger(TimeoutDocumentLockManager.class);

    private final ConcurrentHashMap<DocumentReference, Lock> locks = new ConcurrentHashMap<>();

    private int timeoutValue;
    private TimeUnit timeoutUnit;

    public TimeoutDocumentLockManager() {
        this(10, TimeUnit.SECONDS);
    }

    public TimeoutDocumentLockManager(int timeoutValue, TimeUnit timeoutUnit) {
        this.timeoutValue = timeoutValue;
        this.timeoutUnit = timeoutUnit;
    }

    public void lock(@Nonnull final DocumentReference document)
    {
        try {
            final Lock lock = this.locks.computeIfAbsent(document, k -> new StampedLock().asWriteLock());
            final boolean cleanLock = lock.tryLock(this.timeoutValue, this.timeoutUnit);
            if (!cleanLock) {
                this.logger.debug("Timed out while waiting for lock on [{}], proceeding anyway", document);
            }
        } catch (InterruptedException ex) {
            // We don't expect any interruptions
            this.logger.error("Unexpected interruption while waiting for lock: {}", ex.getMessage(), ex);
        }
    }

    public void unlock(@Nonnull final DocumentReference document)
    {
        Lock lock = this.locks.get(document);
        if (lock == null) {
            return;
        }
        try {
            lock.unlock();
        } catch (IllegalMonitorStateException ex) {
            // Unlocking may fail if a lock timeout resulted in an unclean lock, and then two requests try to unlock
            this.logger.debug("Lock was unexpectedly unlocked already: {}", ex.getMessage(), ex);
        }
    }
}
