package winsome.client;

import java.io.Console;

import winsome.client.exceptions.UnknownCommandException;
import winsome.utils.ConsoleColors;

public enum Command {
    REGISTER        ("register", "Sign up in the WINSOME Social Network"),
    LOGIN           ("login", "Sign in the WINSOME Social Network"),
    LOGOUT          ("logout", "Logout from the WINSOME Social Network"),
    LIST_USERS      ("list users", "Lists the users sharing at least a tag with the currently logged user"),
    LIST_FOLLOWERS  ("list followers", "Lists the followers of the currently logged user"),
    LIST_FOLLOWING  ("list following", "Lists the users followed by the currently logged user"),
    FOLLOW          ("follow", "Follow a user"),
    UNFOLLOW        ("unfollow", "Unfollow a user"),
    BLOG            ("blog", "Lists the posts of a user"),
    POST            ("post", "Creates a new post"),
    SHOW_FEED       ("show feed", "Shows the feed of the current user"),
    SHOW_POST       ("show post", "Shows the post with the given ID"),
    DELETE          ("delete", "Deletes the post with the given ID"),
    REWIN           ("rewin", "Rewins the post with the given ID"),
    RATE            ("rate", "Rates the post with the given ID"),
    COMMENT         ("comment", "Add a comment to the post with the given ID"),
    WALLET          ("wallet", "Prints the current user's wallet"),
    WALLET_BTC      ("wallet btc", "Prints the current user's wallet, converted in BTC"),
    HELP            ("help", "Prints this help message"),
    QUIT            ("quit", "Quits the client");

    public final String name;
    private final String descr;
    Command(String name, String descr){ this.name = name; this.descr = descr; }

    public String getCommandName(){ return name; }
    public String getHelpString(){ return Command.help(this); }

    public static String help(Command cmd){
        switch (cmd) {
            case REGISTER:
                return ConsoleColors.yellow("usage: ") + ConsoleColors.green(cmd.name) + " <username> <password> <tags>\n"
                    + "\t" + cmd.descr + "\n\n"
                    + ConsoleColors.yellow("Arguments\n" + "----------\n")
                    + "<username>     user to register\n"
                    + "<password>     password of the user\n"
                    + "<tag>          tag the user is interested in\n\n"
                    + "There has to be at least one tag and at most five; they are separated by a space.";
            case LOGIN:
                return ConsoleColors.yellow("usage: ") + ConsoleColors.green(cmd.name) + " <username> <password>\n"
                    + "\t" + cmd.descr + "\n\n"
                    + ConsoleColors.yellow("Arguments\n" + "----------\n")
                    + "<username>     user to log into\n"
                    + "<password>     password of the user";
            case LOGOUT:
                return ConsoleColors.yellow("usage: ") + ConsoleColors.green(cmd.name) + "\n"
                    + "\t" + cmd.descr;
            case LIST_USERS:
                return ConsoleColors.yellow("usage: ") + ConsoleColors.green(cmd.name) + "\n"
                    + "\t" + cmd.descr;  
            case LIST_FOLLOWERS:
                return ConsoleColors.yellow("usage: ") + ConsoleColors.green(cmd.name) + "\n"
                    + "\t" + cmd.descr;
            case LIST_FOLLOWING:
                return ConsoleColors.yellow("usage: ") + ConsoleColors.green(cmd.name) + "\n"
                    + "\t" + cmd.descr;
            case FOLLOW:
                return ConsoleColors.yellow("usage: ") + ConsoleColors.green(cmd.name) + " <username>\n"
                    + "\t" + cmd.descr + "\n\n"
                    + ConsoleColors.yellow("Arguments\n" + "----------\n")
                    + "<username>     user to follow";
            case UNFOLLOW:
                return ConsoleColors.yellow("usage: ") + ConsoleColors.green(cmd.name) + " <username>\n"
                    + "\t" + cmd.descr + "\n\n"
                    + ConsoleColors.yellow("Arguments\n" + "----------\n")
                    + "<username>     user to unfollow";
            case BLOG:
                return ConsoleColors.yellow("usage: ") + ConsoleColors.green(cmd.name) + " [<username>]\n"
                    + "\t" + cmd.descr + "\n\n"
                    + ConsoleColors.yellow("Arguments\n" + "----------\n")
                    + "<username>     optional: user whose blog will be shown (default: current user)";
            case POST:
                return ConsoleColors.yellow("usage: ") + ConsoleColors.green(cmd.name) + " <title> <contents>\n"
                    + "\t" + cmd.descr + "\n\n"
                    + ConsoleColors.yellow("Arguments\n" + "----------\n")
                    + "<title>        title of the post (max 20 characters)\n"
                    + "<contents>     contents of the post (max 500 characters)";
            case SHOW_FEED:
                return ConsoleColors.yellow("usage: ") + ConsoleColors.green(cmd.name) + "\n"
                    + "\t" + cmd.descr;
            case SHOW_POST:
                return ConsoleColors.yellow("usage: ") + ConsoleColors.green(cmd.name) + " <idPost>\n"
                    + "\t" + cmd.descr + "\n\n"
                    + ConsoleColors.yellow("Arguments\n" + "----------\n")
                    + "<idPost>       ID of the post";
            case DELETE:
                return ConsoleColors.yellow("usage: ") + ConsoleColors.green(cmd.name) + " <idPost>\n"
                    + "\t" + cmd.descr + "\n\n"
                    + ConsoleColors.yellow("Arguments\n" + "----------\n")
                    + "<idPost>       ID of the post";
            case REWIN:
                return ConsoleColors.yellow("usage: ") + ConsoleColors.green(cmd.name) + " <idPost>\n"
                    + "\t" + cmd.descr + "\n\n"
                    + ConsoleColors.yellow("Arguments\n" + "----------\n")
                    + "<idPost>       ID of the post";
            case RATE:
                return ConsoleColors.yellow("usage: ") + ConsoleColors.green(cmd.name) + " <idPost> <vote>\n"
                    + "\t" + cmd.descr + "\n\n"
                    + ConsoleColors.yellow("Arguments\n" + "----------\n")
                    + "<idPost>       ID of the post to vote\n"
                    + "<vote>         vote given (may be \"+1\" or \"-1\"";
            case COMMENT:
                return ConsoleColors.yellow("usage: ") + ConsoleColors.green(cmd.name) + " <idPost> <comment>\n"
                    + "\t" + cmd.descr + "\n\n"
                    + ConsoleColors.yellow("Arguments\n" + "----------\n")
                    + "<idPost>       ID of the post\n"
                    + "<comment>      contents of the comment";
            case WALLET:
                return ConsoleColors.yellow("usage: ") + ConsoleColors.green(cmd.name) + "\n"
                    + "\t" + cmd.descr;
            case WALLET_BTC:
                return ConsoleColors.yellow("usage: ") + ConsoleColors.green(cmd.name) + "\n"
                    + "\t" + cmd.descr;
            case QUIT:
                return ConsoleColors.yellow("usage: ") + ConsoleColors.green(cmd.name) + "\n"
                    + "\t" + cmd.descr;
            default:
                return help();
        }        
    }

    public static String help(){
        int length = 20;
        String cmdsHelp = "";
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

    public static Command fromString(String str) throws UnknownCommandException {
        if(str == null) throw new NullPointerException();

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

        throw new UnknownCommandException("\"" + str + "\" is not a known command");
    }

    private static boolean startsWithCmd(String str, Command cmd){ 
        return str.equals(cmd.name) || str.startsWith(cmd.name + " ");
    }

}
