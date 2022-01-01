package winsome.api.exceptions;

public class MalformedJSONException extends Exception {
    public MalformedJSONException(){ super(); }
    public MalformedJSONException(String msg){ super(msg); }
    public MalformedJSONException(Throwable err){ super(err); }
    public MalformedJSONException(String msg, Throwable err){ super(msg, err); }
}
