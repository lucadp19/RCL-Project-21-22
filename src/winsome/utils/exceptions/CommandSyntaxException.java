package winsome.utils.exceptions;

public class CommandSyntaxException extends Exception {
    public CommandSyntaxException(){ super(); }
    public CommandSyntaxException(String msg){ super(msg); }
    public CommandSyntaxException(String msg, Throwable err){ super(msg, err); }
}
