package winsome.api;

import java.util.*;

public class Wallet {
    public final double total;
    private final List<TransactionInfo> transactions;

    public Wallet(double total, Collection<TransactionInfo> transactions){
        this.total = total;
        this.transactions = new ArrayList<>(
            Objects.requireNonNull(transactions, "null collection of transactions")
        );
    }

    public List<TransactionInfo> getTransactions(){ return new ArrayList<>(transactions); }
}
