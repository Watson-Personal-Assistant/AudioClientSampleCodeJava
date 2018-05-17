package wa.util;
/**
 * Copyright 2016-2017 IBM Corporation. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Debouncer <T> {
    // Initialize our logger
    private static final Logger LOG = LogManager.getLogger(Debouncer.class);

    private final ScheduledExecutorService sched = Executors.newScheduledThreadPool(1);
    private final ConcurrentHashMap<T, TimerTask> delayedMap = new ConcurrentHashMap<T, TimerTask>();
    private final Runnable finalTask;
    private final int interval;
    private T lastKey;
    private final Object lock = new Object();

    public Debouncer(Runnable finalTask, int interval) {
        this.finalTask = finalTask;
        this.interval = interval;
        this.lastKey = null;
    }

    public void call(T key) {
        synchronized(lock) {
            lastKey = key;
        }
        TimerTask task = new TimerTask(key);

        TimerTask prev;
        do {
            synchronized(lock) {
                prev = delayedMap.putIfAbsent(key, task);
                if (prev == null) {
                    sched.schedule(task, interval, TimeUnit.MILLISECONDS);
                }
            }
        }
        while (prev != null && !prev.extend()); // Exit only if new task was added to map, or existing task was extended successfully
    }

    public void terminate() {
        synchronized(lock) {
            sched.shutdownNow();
        }
    }

    // The task that wakes up when the wait time elapses
    private class TimerTask implements Runnable {
        private final T key;
        private long dueTime;

        public TimerTask(T key) {
            this.key = key;
            extend();
        }

        public boolean extend() {
            synchronized (lock) {
                // Has task been shutdown?
                if (dueTime < 0) {
                    // Yes, task has been shutdown
                    return false;
                }
                dueTime = System.currentTimeMillis() + interval;
                return true;
            }
        }

        public void run() {
            synchronized (lock) {
                long remaining = dueTime - System.currentTimeMillis();
                if (remaining > 0) { // Re-schedule task
                    sched.schedule(this, remaining, TimeUnit.MILLISECONDS);
                } else { // Mark as terminated and invoke callback
                    dueTime = -1;
                    try {
                        if (key.equals(lastKey)) {
                            LOG.info("TimerTask keys match - running finalTask");
                            finalTask.run();
                        }
                        else {
                            LOG.info("TimerTask key does not match last key - not running finalTask.");
                        }
                    } finally {
                        delayedMap.remove(key);
                    }
                }
            }
        }
    }
}
