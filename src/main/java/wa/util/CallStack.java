package wa.util;

public class CallStack extends Throwable {
    private static final long serialVersionUID = 1L;

    public CallStack() {
        super ("Called from...");
    }
    public CallStack(String info) {
        super(info);
    }
}
