package winsome.client;

public enum Command {
    REGISTER        ("register", "Sign up in the WINSOME Social Network"),
    LOGIN           ("login", "Sign in the WINSOME Social Network"),
    LOGOUT          ("logout", "Logout from the WINSOME Social Network"),
    LIST_USERS      ("list users", "Lists the users sharing at least a tag with the currently logged user"),
    LIST_FOLLOWERS  ("list followers", "Lists the followers of the currently logged user"),
    LIST_FOLLOWING  ("list following", "Lists the users followed by the currently logged user"),
    FOLLOW          ("follow", "Follow a user"),
    UNFOLLOW        ("unfollow", "Unfollow a user"),
    BLOG            ("blog", "Lists the current user's posts"),
    POST            ("post", "Creates a new post"),
    SHOW_FEED       ("show feed", "Shows the feed of the current user"),
    SHOW_POST       ("show post", "Shows the post with the given ID"),
    DELETE          ("delete", "Deletes the post with the given ID"),
    REWIN           ("rewin", "Rewins the post with the given ID"),
    RATE            ("rate", "Rates the post with the given ID"),
    COMMENT         ("comment", "Add a comment to the post with the given ID"),
    WALLET          ("wallet", "Prints the current user's wallet"),
    WALLET_BTC      ("wallet btc", "Prints the current user's wallet, converted in BTC"),
    HELP            ("help", "Prints this help message");

    public final String name;
    private final String descr;
    Command(String name, String descr){ this.name = name; this.descr = descr; }

    public String getCommandName(){ return name; }
    public String getHelpString(){ return Command.help(this); }

    public static String help(Command cmd){
        switch (cmd) {
            case REGISTER:
                return "usage: " + cmd.name + " <username> <password> [<tag>{ <tag>}]\n"
                    + "\t" + cmd.descr + "\n\n"
                    + "Arguments\n" + "----------\n"
                    + "<username>     user to register\n"
                    + "<password>     password of the user\n"
                    + "<tag>          tag the user is interested in\n\n"
                    + "tags can be at most five and they are separated by a space";
            case LOGIN:
                return "usage: " + cmd.name + " <username> <password>\n"
                    + "\t" + cmd.descr + "\n\n"
                    + "Arguments\n" + "----------\n"
                    + "<username>     user to log into\n"
                    + "<password>     password of the user";
            case LOGOUT:
                return "usage: " + cmd.name + "\n"
                    + "\t" + cmd.descr + "\n";
            case LIST_USERS:
                return "usage: " + cmd.name + "\n"
                    + "\t" + cmd.descr;  
            case LIST_FOLLOWERS:
                return "usage: " + cmd.name + "\n"
                    + "\t" + cmd.descr;
            case LIST_FOLLOWING:
                return "usage: " + cmd.name + "\n"
                    + "\t" + cmd.descr;
            case FOLLOW:
                return "usage: " + cmd.name + " <username>\n"
                    + "\t" + cmd.descr + "\n\n"
                    + "Arguments\n" + "----------\n"
                    + "<username>     user to follow";
            case UNFOLLOW:
                return "usage: " + cmd.name + " <username>\n"
                    + "\t" + cmd.descr + "\n\n"
                    + "Arguments\n" + "----------\n"
                    + "<username>     user to unfollow";
            case BLOG:
                return "usage: " + cmd.name + "\n"
                    + "\t" + cmd.descr;
            case POST:
                return "usage: " + cmd.name + " <title> <contents>\n"
                    + "\t" + cmd.descr + "\n\n"
                    + "Arguments\n" + "----------\n"
                    + "<title>        title of the post (max 20 characters)\n"
                    + "<contents>     contents of the post (max 500 characters)";
            case SHOW_FEED:
                return "usage: " + cmd.name + "\n"
                    + "\t" + cmd.name;
            case SHOW_POST:
                return "usage: " + cmd.name + " <idPost>\n"
                    + "\t" + cmd.descr + "\n\n"
                    + "Arguments\n" + "----------\n"
                    + "<idPost>       ID of the post";
            case DELETE:
                return "usage: " + cmd.name + " <idPost>\n"
                    + "\t" + cmd.descr + "\n\n"
                    + "Arguments\n" + "----------\n"
                    + "<idPost>       ID of the post";
            case REWIN:
                return "usage: " + cmd.name + " <idPost>\n"
                    + "\t" + cmd.descr + "\n\n"
                    + "Arguments\n" + "----------\n"
                    + "<idPost>       ID of the post";
            case RATE:
                return "usage: " + cmd.name + " <idPost> <vote>\n"
                    + "\t" + cmd.descr + "\n\n"
                    + "Arguments\n" + "----------\n"
                    + "<idPost>       ID of the post to vote\n"
                    + "<vote>         vote given (may be \"+1\" or \"-1\"";
            case COMMENT:
                return "usage: " + cmd.name + " <idPost> <comment>\n"
                    + "\t" + cmd.descr + "\n\n"
                    + "Arguments\n" + "----------\n"
                    + "<idPost>       ID of the post\n"
                    + "<comment>      contents of the comment";
            case WALLET:
                return "usage: " + cmd.name + "\n"
                    + "\t" + cmd.descr;
            case WALLET_BTC:
                return "usage: " + cmd.name + "\n"
                    + "\t" + cmd.descr;
            default:
                return help();
        }        
    }

    public static String help(){
        int length = 20;
        String cmdsHelp = "";
        for(Command cmd : Command.values()){
            cmdsHelp += String.format("%1$-" + length + "s", cmd.name) +
                        cmd.descr + "\n";
        }

        return "usage: <CMD> ...\n"
            + "\tClient interface for the WINSOME Social Network\n\n"
            + "Available commands\n"
            + "------------------\n"
            + cmdsHelp;
    }

}
