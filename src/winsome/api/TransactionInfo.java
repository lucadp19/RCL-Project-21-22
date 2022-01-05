package winsome.api;

import java.time.Instant;
import java.util.Objects;

public class TransactionInfo {
    public final double amount;
    public final Instant timestamp;

    public TransactionInfo(double amount, Instant timestamp){
        this.amount = amount;
        this.timestamp = Objects.requireNonNull(timestamp, "null timestamp");
    }
}
