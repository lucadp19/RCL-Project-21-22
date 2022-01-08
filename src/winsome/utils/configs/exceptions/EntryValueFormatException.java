package winsome.utils.configs.exceptions;

/** Thrown whenever the value of an entry is in a wrong format (e.g. an integer was expected, but the value was a string) */
public class EntryValueFormatException extends InvalidConfigFileException {
    public EntryValueFormatException(){ super(); }
    public EntryValueFormatException(String msg){ super(msg); }
    public EntryValueFormatException(Throwable err){ super(err); }
    public EntryValueFormatException(String msg, Throwable err){ super(msg, err); }
}
