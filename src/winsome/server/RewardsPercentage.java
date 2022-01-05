package winsome.server;

/** A class representing the percentage of the rewards going to the author and to the curators. */
public class RewardsPercentage {
    /** The percentage of the rewards going to the author, represented as a number between 0 and 1*/
    public final double author;
    /** The percentage of the rewards going to the curators, represented as a number between 0 and 1*/
    public final double curator;

    /**
     * Creates a RewardsPercentage object from the author percentage.
     * @param author the percentage of the rewards going to the author, represented as a number between 0 and 1
     */
    private RewardsPercentage(double author){
        if(author < 0 || author > 1) throw new IllegalArgumentException("author percentage must be between 0 and 1");

        this.author = author;
        this.curator = 1 - author;
    }
    
    /**
     * Creates a RewardsPercentage object from the author percentage.
     * @param authorPerc the percentage of the rewards going to the author, represented as a number between 0 and 1
     * @return the resulting RewardsPercentage object
     */
    public static RewardsPercentage fromAuthor(double authorPerc){ 
        try { return new RewardsPercentage(authorPerc); }
        catch(IllegalArgumentException ex){ throw new IllegalArgumentException("author percentage must be between 0 and 1"); }
    }
    /**
     * Creates a RewardsPercentage object from the curators percentage.
     * @param curatorPerc the percentage of the rewards going to the curators, represented as a number between 0 and 1
     * @return the resulting RewardsPercentage object
     */
    public static RewardsPercentage fromCurator(double curatorPerc){ 
        try { return new RewardsPercentage(1-curatorPerc); }
        catch(IllegalArgumentException ex){ throw new IllegalArgumentException("curator percentage must be between 0 and 1"); }
    }
}
