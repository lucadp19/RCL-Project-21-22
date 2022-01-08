package winsome.api.userstructs;

import java.time.Instant;
import java.util.Objects;

/** The information contained in a Winsome Transaction */
public class TransactionInfo {
    /** The amount of Wincoins */
    public final double amount;
    /** The timestamp at which the transaction was generated */
    public final Instant timestamp;

    /**
     * Records a new Transaction.
     * @param amount the amount of Wincoins
     * @param timestamp the timestamp
     */
    public TransactionInfo(double amount, Instant timestamp){
        this.amount = amount;
        this.timestamp = Objects.requireNonNull(timestamp, "timestamp must not be null");
    }
}
