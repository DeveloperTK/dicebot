package de.foxat.dicebot;

import de.foxat.dicebot.command.CommandListener;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;

import javax.security.auth.login.LoginException;

public class DiceBot {

    private final JDA bot;
    private final CommandListener commandListener;

    public DiceBot(String token) throws LoginException {
        bot = JDABuilder.createDefault(token).build();
        commandListener = new CommandListener();
    }

    public void start() throws InterruptedException {
        bot.awaitReady();

        bot.addEventListener(commandListener);
        commandListener.registerCommand(bot);
    }

    public static void main(String[] args) throws LoginException {
        try {
            String token = System.getenv("token");
            if (token == null) throw new NullPointerException("Missing 'token' environment variable");
            DiceBot instance = new DiceBot(token);
            instance.start();
        } catch (InterruptedException exception) {
            exception.printStackTrace();
        }
    }

}
