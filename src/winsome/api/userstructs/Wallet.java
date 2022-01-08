package winsome.api.userstructs;

import java.util.*;

/** The wallet of a User, containing the total amount of Wincoins and the transaction history */
public class Wallet {
    /** The total amount of Wincoins */
    public final double total;
    /** The transaction history */
    private final List<TransactionInfo> transactions;

    public Wallet(double total, Collection<TransactionInfo> transactions){
        this.total = total;
        this.transactions = new ArrayList<>(
            Objects.requireNonNull(transactions, "null collection of transactions")
        );
    }

    /**
     * Returns the transaction history.
     * @return the transaction history
     */
    public List<TransactionInfo> getTransactions(){ return new ArrayList<>(transactions); }
}
