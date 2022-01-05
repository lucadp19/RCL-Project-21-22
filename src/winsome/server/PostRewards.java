package winsome.server;

import java.util.ArrayList;
import java.util.*;

/** The reward calculated by the Rewards Algorithm on a Post */
public class PostRewards {
    /** The reward in Wincoins */
    public final double reward;
    /** The curators of a post */
    private final List<String> curators;

    /**
     * Creates a new reward.
     * @param reward the amount of the reward, in Wincoins
     * @param curators the curators of the post
     */
    public PostRewards(double reward, Collection<String> curators){
        if(curators == null) throw new NullPointerException("null argument");

        this.reward = reward;
        this.curators = new ArrayList<>(curators);
    }

    /**
     * Returns the amount of Wincoins going to the author of the post.
     * @param authorPercentage the percentage of the total reward going to the author
     * @return the amount of Wincoins going to the author
     */
    public double authorReward(double authorPercentage){
        return reward * authorPercentage;
    }

    /**
     * Returns the amount of Wincoins going to a single curator of this post.
     * @param authorPercentage the percentage of the total reward going to the author
     * @return the amount of Wincoins going to a single curator
     */
    public double curatorReward(double authorPercentage){
        if(curators.size() == 0) return 0;
        return (reward * (1 - authorPercentage)) / curators.size();
    }

    /**
     * Returns the usernames of the curators of this post.
     * @return the curators of this post
     */
    public List<String> getCurators(){ return new ArrayList<>(curators); }
}
