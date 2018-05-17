package wa.util;
import java.util.concurrent.CopyOnWriteArraySet;

import wa.client.ThreadManager;

import java.util.Set;

public abstract class NotifyingThread extends Thread {
  private final Set<ThreadManager> listeners
                   = new CopyOnWriteArraySet<ThreadManager>();
  public final void addListener(final ThreadManager listener) {
    listeners.add(listener);
  }
  public final void removeListener(final ThreadManager listener) {
    listeners.remove(listener);
  }

  private final void notifyListenersStop() {
    for (ThreadManager listener : listeners) {
      listener.notifyOfThreadStop(this);
    }
  }

  private final void notifyListenersStart() {
    for (ThreadManager listener : listeners) {
      listener.notifyOfThreadStart(this);
    }
  }

  @Override
  public final void run() {
    try {
      notifyListenersStart();
      doRun();
    } finally {
      notifyListenersStop();
    }
  }
  public abstract void doRun();
}
