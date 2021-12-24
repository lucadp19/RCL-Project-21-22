package test.winsome.commands;

import winsome.client.*;
import winsome.utils.*;
import winsome.utils.exceptions.*;

public class ClientCommandsTest {
    public static void main(String[] args) {
        // printing all help commands
        System.out.println(ConsoleColors.GREEN_BOLD + "\tTesting help messages" + ConsoleColors.RESET);
        
        for(Command cmd : Command.values()){
            System.out.println();
            System.out.println(
                ConsoleColors.GREEN_BOLD + "[*] " + ConsoleColors.RESET +
                "Generating \"" + cmd.getCommandName() + "\" command help text..."
                );
            System.out.println();
            System.out.println(Command.help(cmd));
        }
    }
}
