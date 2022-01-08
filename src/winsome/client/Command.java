package winsome.client;

import java.util.Objects;

import winsome.client.exceptions.UnknownCommandException;
import winsome.utils.ConsoleColors;

/** A Command in the Winsome Client CLI */
public enum Command {
    /** Used to sign up a new user in the Social Network */
    REGISTER        ("register", "Sign up in the WINSOME Social Network"),
    /** Used to log into an existing user */
    LOGIN           ("login", "Sign in the WINSOME Social Network"),
    /** Used to log out of an existing user */
    LOGOUT          ("logout", "Logout from the WINSOME Social Network"),
    /** Used to list the other users of the Social Network */
    LIST_USERS      ("list users", "Lists the users sharing at least a tag with the currently logged user"),
    /** Used to list all followers of the currently logged user */
    LIST_FOLLOWERS  ("list followers", "Lists the followers of the currently logged user"),
    /** Used to list all users followed by the currently logged user */
    LIST_FOLLOWING  ("list following", "Lists the users followed by the currently logged user"),
    /** Used to follow a user */
    FOLLOW          ("follow", "Follow a user"),
    /** Used to unfollow a user */
    UNFOLLOW        ("unfollow", "Unfollow a user"),
    /** Used to list the posts of this or another user */
    BLOG            ("blog", "Lists the posts of a user"),
    /** Used to create a new post */
    POST            ("post", "Creates a new post"),
    /** Used to show the feed of the current user */
    SHOW_FEED       ("show feed", "Shows the feed of the current user"),
    /** Used to show the post with the given ID */
    SHOW_POST       ("show post", "Shows the post with the given ID"),
    /** Used to delete the post with the given ID */
    DELETE          ("delete", "Deletes the post with the given ID"),
    /** Used to rewin the post with the given ID */
    REWIN           ("rewin", "Rewins the post with the given ID"),
    /** Used to rate the post with the given ID */
    RATE            ("rate", "Rates the post with the given ID"),
    /** Used to add a comment to the post with the given ID */
    COMMENT         ("comment", "Add a comment to the post with the given ID"),
    /** Used to get the current user's wallet */
    WALLET          ("wallet", "Prints the current user's wallet"),
    /** Used to get the current user's wallet, converted in BTC */
    WALLET_BTC      ("wallet btc", "Prints the current user's wallet, converted in BTC"),
    /** Prints the help message */
    HELP            ("help", "Prints this help message"),
    /** Quits the client */
    QUIT            ("quit", "Quits the client");

    /** Name of the command */
    public final String name;
    /** Description of the command */
    private final String descr;

    Command(String name, String descr){ 
        this.name = Objects.requireNonNull(name, "command name must not be null"); 
        this.descr = Objects.requireNonNull(descr, "command description must not be null"); 
    }

    /**
     * Returns the help string of this command
     * @return the help string of this
     */
    public String getHelpString(){ return Command.help(this); }

    /**
     * Returns the help string of the given command.
     * @param cmd the given command
     * @return the help string
     */
    public static String help(Command cmd){
        Objects.requireNonNull(cmd, "command must not be null");

        return switch (cmd) {
            case REGISTER -> 
                ConsoleColors.yellow("usage: ") + ConsoleColors.green(cmd.name) + " <username> <password> <tags>\n"
                    + "\t" + cmd.descr + "\n\n"
                    + ConsoleColors.yellow("Arguments\n" + "----------\n")
                    + "<username>     user to register\n"
                    + "<password>     password of the user\n"
                    + "<tag>          tag the user is interested in\n\n"
                    + "There has to be at least one tag and at most five; they are separated by a space.";
            case LOGIN ->
                ConsoleColors.yellow("usage: ") + ConsoleColors.green(cmd.name) + " <username> <password>\n"
                    + "\t" + cmd.descr + "\n\n"
                    + ConsoleColors.yellow("Arguments\n" + "----------\n")
                    + "<username>     user to log into\n"
                    + "<password>     password of the user";
            case LOGOUT ->
                ConsoleColors.yellow("usage: ") + ConsoleColors.green(cmd.name) + "\n"
                    + "\t" + cmd.descr;
            case LIST_USERS ->
                ConsoleColors.yellow("usage: ") + ConsoleColors.green(cmd.name) + "\n"
                    + "\t" + cmd.descr;  
            case LIST_FOLLOWERS ->
                ConsoleColors.yellow("usage: ") + ConsoleColors.green(cmd.name) + "\n"
                    + "\t" + cmd.descr;
            case LIST_FOLLOWING ->
                ConsoleColors.yellow("usage: ") + ConsoleColors.green(cmd.name) + "\n"
                    + "\t" + cmd.descr;
            case FOLLOW ->
                ConsoleColors.yellow("usage: ") + ConsoleColors.green(cmd.name) + " <username>\n"
                    + "\t" + cmd.descr + "\n\n"
                    + ConsoleColors.yellow("Arguments\n" + "----------\n")
                    + "<username>     user to follow";
            case UNFOLLOW ->
                ConsoleColors.yellow("usage: ") + ConsoleColors.green(cmd.name) + " <username>\n"
                    + "\t" + cmd.descr + "\n\n"
                    + ConsoleColors.yellow("Arguments\n" + "----------\n")
                    + "<username>     user to unfollow";
            case BLOG ->
                ConsoleColors.yellow("usage: ") + ConsoleColors.green(cmd.name) + " [<username>]\n"
                    + "\t" + cmd.descr + "\n\n"
                    + ConsoleColors.yellow("Arguments\n" + "----------\n")
                    + "<username>     optional: user whose blog will be shown (default: current user)";
            case POST ->
                ConsoleColors.yellow("usage: ") + ConsoleColors.green(cmd.name) + " <title> <contents>\n"
                    + "\t" + cmd.descr + "\n\n"
                    + ConsoleColors.yellow("Arguments\n" + "----------\n")
                    + "<title>        title of the post (max 20 characters)\n"
                    + "<contents>     contents of the post (max 500 characters)";
            case SHOW_FEED ->
                ConsoleColors.yellow("usage: ") + ConsoleColors.green(cmd.name) + "\n"
                    + "\t" + cmd.descr;
            case SHOW_POST ->
                ConsoleColors.yellow("usage: ") + ConsoleColors.green(cmd.name) + " <idPost>\n"
                    + "\t" + cmd.descr + "\n\n"
                    + ConsoleColors.yellow("Arguments\n" + "----------\n")
                    + "<idPost>       ID of the post";
            case DELETE ->
                ConsoleColors.yellow("usage: ") + ConsoleColors.green(cmd.name) + " <idPost>\n"
                    + "\t" + cmd.descr + "\n\n"
                    + ConsoleColors.yellow("Arguments\n" + "----------\n")
                    + "<idPost>       ID of the post";
            case REWIN ->
                ConsoleColors.yellow("usage: ") + ConsoleColors.green(cmd.name) + " <idPost>\n"
                    + "\t" + cmd.descr + "\n\n"
                    + ConsoleColors.yellow("Arguments\n" + "----------\n")
                    + "<idPost>       ID of the post";
            case RATE ->
                ConsoleColors.yellow("usage: ") + ConsoleColors.green(cmd.name) + " <idPost> <vote>\n"
                    + "\t" + cmd.descr + "\n\n"
                    + ConsoleColors.yellow("Arguments\n" + "----------\n")
                    + "<idPost>       ID of the post to vote\n"
                    + "<vote>         vote given (may be \"+1\" or \"-1\")";
            case COMMENT ->
                ConsoleColors.yellow("usage: ") + ConsoleColors.green(cmd.name) + " <idPost> <comment>\n"
                    + "\t" + cmd.descr + "\n\n"
                    + ConsoleColors.yellow("Arguments\n" + "----------\n")
                    + "<idPost>       ID of the post\n"
                    + "<comment>      contents of the comment";
            case WALLET ->
                ConsoleColors.yellow("usage: ") + ConsoleColors.green(cmd.name) + "\n"
                    + "\t" + cmd.descr;
            case WALLET_BTC ->
                ConsoleColors.yellow("usage: ") + ConsoleColors.green(cmd.name) + "\n"
                    + "\t" + cmd.descr;
            case QUIT ->
                ConsoleColors.yellow("usage: ") + ConsoleColors.green(cmd.name) + "\n"
                    + "\t" + cmd.descr;
            default -> help();
        };        
    }

    /**
     * Returns the general help message.
     * @return the general help message
     */
    public static String help(){
        int length = 20;
        String cmdsHelp = "";
        // table of commands
        for(Command cmd : Command.values()){
            cmdsHelp += "\n" + ConsoleColors.green(String.format("%1$-" + length + "s", cmd.name)) +
                        cmd.descr;
        }

        return ConsoleColors.yellow("usage: ") + ConsoleColors.green("command") + " [arguments]\n"
            + "\tClient interface for the WINSOME Social Network\n\n"
            + ConsoleColors.yellow("Available commands\n")
            + ConsoleColors.yellow("------------------")
            + cmdsHelp
            + "\n\nSee " + ConsoleColors.green("help <command>") + " to get help on a specific command.";
    }

    /**
     * Parses the command type from a string.
     * @param str the given string
     * @return the parsed command type
     * @throws UnknownCommandException if no command corresponds to the given string
     */
    public static Command fromString(String str) throws UnknownCommandException {
        Objects.requireNonNull(str, "the given string must not be null");

        if(startsWithCmd(str, HELP))            return HELP;
        if(startsWithCmd(str, QUIT))            return QUIT;
        if(startsWithCmd(str, REGISTER))        return REGISTER;
        if(startsWithCmd(str, LOGIN))           return LOGIN;
        if(startsWithCmd(str, LOGOUT))          return LOGOUT;
        if(startsWithCmd(str, LIST_USERS))      return LIST_USERS;
        if(startsWithCmd(str, LIST_FOLLOWERS))  return LIST_FOLLOWERS;
        if(startsWithCmd(str, LIST_FOLLOWING))  return LIST_FOLLOWING;
        if(startsWithCmd(str, FOLLOW))          return FOLLOW;
        if(startsWithCmd(str, UNFOLLOW))        return UNFOLLOW;
        if(startsWithCmd(str, BLOG))            return BLOG;
        if(startsWithCmd(str, POST))            return POST;
        if(startsWithCmd(str, SHOW_FEED))       return SHOW_FEED;
        if(startsWithCmd(str, SHOW_POST))       return SHOW_POST;
        if(startsWithCmd(str, DELETE))          return DELETE;
        if(startsWithCmd(str, REWIN))           return REWIN;
        if(startsWithCmd(str, RATE))            return RATE;
        if(startsWithCmd(str, COMMENT))         return COMMENT;
        if(startsWithCmd(str, WALLET_BTC))      return WALLET_BTC;
        if(startsWithCmd(str, WALLET))          return WALLET;

        // no matching command
        throw new UnknownCommandException("\"" + str + "\" is not a known command");
    }

    /**
     * Checks if a string starts with a given command.
     * @param str the given string
     * @param cmd the given command
     * @return true if and only if the string starts with the given command
     */
    private static boolean startsWithCmd(String str, Command cmd){ 
        Objects.requireNonNull(str, "the given string must not be null");
        Objects.requireNonNull(cmd, "the given command must not be null");

        return str.equals(cmd.name) || str.startsWith(cmd.name + " ");
    }

}
