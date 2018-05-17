package wa.client;
public interface ThreadManager {
    public void notifyOfThreadStart(final Thread thread);
    public void notifyOfThreadStop(final Thread thread);
}
