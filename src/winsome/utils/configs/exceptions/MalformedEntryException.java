package winsome.utils.configs.exceptions;

/** Thrown whenever an entry in the config file was malformed */
public class MalformedEntryException extends InvalidConfigFileException {
    public MalformedEntryException(){ super(); }
    public MalformedEntryException(String msg){ super(msg); }
    public MalformedEntryException(Throwable err){ super(err); }
    public MalformedEntryException(String msg, Throwable err){ super(msg, err); }
}
