package winsome.api.exceptions;

public class FollowException extends Exception {
    public FollowException(){ super(); }
    public FollowException(String msg){ super(msg); }
    public FollowException(Throwable err){ super(err); }
    public FollowException(String msg, Throwable err){ super(msg, err); }
}
