package winsome.api.exceptions;

public class AlreadyFollowingException extends Exception {
    public AlreadyFollowingException(){ super(); }
    public AlreadyFollowingException(String msg){ super(msg); }
    public AlreadyFollowingException(Throwable err){ super(err); }
    public AlreadyFollowingException(String msg, Throwable err){ super(msg, err); }
}
