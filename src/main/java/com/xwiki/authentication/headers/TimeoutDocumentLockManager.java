/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
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
 * TimeoutDocumentLockManager from PhenoTips. This manager will accept a lock request even if the lock
 * couldn't be obtained when a timeout interval (10 seconds) has elapsed.
 *
 * @version $Id$
 */
public class TimeoutDocumentLockManager {

    private Logger logger = LoggerFactory.getLogger(TimeoutDocumentLockManager.class);

    private final ConcurrentHashMap<DocumentReference, Lock> locks = new ConcurrentHashMap<>();

    private int timeoutValue;
    private TimeUnit timeoutUnit;

    /**
     * Default constructor.
     */
    public TimeoutDocumentLockManager() {
        this(10, TimeUnit.SECONDS);
    }

    /**
     * Constructor.
     * @param timeoutValue  the number of timeoutUnits to wait until a lock can be bypassed.
     * @param timeoutUnit   {@link TimeUnit} to apply to the timeout value
     */
    public TimeoutDocumentLockManager(int timeoutValue, TimeUnit timeoutUnit) {
        this.timeoutValue = timeoutValue;
        this.timeoutUnit = timeoutUnit;
    }

    /**
     * Lock a document. This method will block until the lock is successfully obtained.
     *
     * @param document the document to lock
     */
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

    /**
     * Unlock a document.
     *
     * @param document the document to unlock
     */
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
