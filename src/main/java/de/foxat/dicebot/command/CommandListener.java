package de.foxat.dicebot.command;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionBuilder;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.security.SecureRandom;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CommandListener extends ListenerAdapter {

    private static final String MAIN_COMMAND_NAME = "r";
    private static final String HELP_COMMAND_NAME = "r-help";

    private final AtomicInteger sharedCounter;

    public CommandListener() {
        this.sharedCounter = new AtomicInteger(0);
    }

    public void registerCommand(JDA jda) {
        jda.upsertCommand("r-help", "Mehr informationen über den Würfel-Bot").queue();
        jda.upsertCommand(
                new CommandData("r", "Neuen Würfelwurf erstellen").addOptions(
                        new OptionData(OptionType.STRING, "ausdruck", "Der Würfelausdruck, der berechnet werden soll", true),
                        new OptionData(OptionType.STRING, "name", "Wofür ist der Wurf?", false),
                        new OptionData(OptionType.BOOLEAN, "verbos", "Soll die Berechnung ausführlich angezeigt werden?", false)
                )
        ).queue();
    }

    @Override
    public void onSlashCommand(@NotNull SlashCommandEvent event) {
        try {
            execute(event);
        } catch (ExecutionException exception) {
            event.reply(exception.getMessage()).setEphemeral(true).queue();
        } catch (NullPointerException exception) {
            exception.printStackTrace();
            if (!event.isAcknowledged()) {
                event.reply("An unknown error occurred! (NullPointerException)").setEphemeral(true).queue();
            }
        }
    }

    private void execute(SlashCommandEvent event) throws NullPointerException {
        if (event.getName().equals(MAIN_COMMAND_NAME)) {
            try {
                Objects.requireNonNull(event.getOption("ausdruck"));
            } catch (NullPointerException exception) {
                throw new ExecutionException("Bitte gib alle Argumente an (ausdruck, name)");
            }

            boolean verbose = false;
            if (event.getOption("verbos") != null) {
                verbose = Objects.requireNonNull(event.getOption("verbos")).getAsBoolean();
            }

            String expression = Objects.requireNonNull(event.getOption("ausdruck")).getAsString();

            String name;
            if (event.getOption("name") != null) {
                name = Objects.requireNonNull(event.getOption("name")).getAsString();
            } else {
                name = "Wurf #" + sharedCounter.incrementAndGet();
            }

            event.reply(runExpressionParsing(expression, name, event.getUser(), verbose)).queue();
        } else if (event.getName().equals(HELP_COMMAND_NAME)) {
            event.replyEmbeds(getHelpMessageBuilder().build()).queue();
        }
    }

    private Message runExpressionParsing(String expression, String name, User user, boolean verbose) {
        String regex = "([0-9]+[d][0-9]+)";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(expression);

        List<DieRecord> records = new ArrayList<>();
        String parsedExpression = expression;

        while (matcher.find()) {
            try {
                DieRecord current = new DieRecord(records.size() + 1, matcher.group());
                records.add(current);
                parsedExpression = parsedExpression.replace(matcher.group(), String.valueOf(current.result));
            } catch (NumberFormatException exception) {
                throw new ExecutionException("Error in dice pattern: "
                        + matcher.group() + " - " + exception.getMessage());
            }
        }

        MessageBuilder messageBuilder = new MessageBuilder("Wurf `").append(expression).append("` von ")
                .append(user.getAsMention()).append("\n")
                .append("__Name:__ ").append(name).append("\n")
                .append("__Würfel:__ ");

        if (records.size() < 2) {
            for (DieRecord record : records) {
                messageBuilder.append(record.expression).append(": ")
                        .append(record.calculation).append(" = ").append(record.result).append("\n");
            }
        } else {
            messageBuilder.append("\n");
            for (DieRecord record : records) {
                messageBuilder.append("  - ").append(record.expression).append(": ")
                        .append(record.calculation).append(" = ").append(record.result).append("\n");
            }
        }

        long result;

        try {
            Expression mathExp = new ExpressionBuilder(parsedExpression).build();
            result = (long) mathExp.evaluate();
        } catch (RuntimeException exception) {
            throw new ExecutionException("Error in dice pattern: `" + expression + "`");
        }

        messageBuilder.append("\n")
                .append("__Ergebnis:__ ").append(parsedExpression).append(" = **").append(result).append("**");

        if (verbose) {
            return messageBuilder.build();
        } else {
            if (records.size() == 1) {
                return new MessageBuilder()
                        .append("**").append(name).append("**: ").append(expression).append(" (")
                        .append(String.join(", ", records.get(0).calculation)).append(")").append("\n")
                        .append("**Ergebnis:** ").append(String.valueOf(result))
                        .build();
            } else {
                return new MessageBuilder()
                        .append("**").append(name).append("**: ").append(expression).append("\n")
                        .append("**Ergebnis:** ").append(String.valueOf(result))
                        .build();
            }
        }
    }

    private EmbedBuilder getHelpMessageBuilder() {
        return new EmbedBuilder()
                .setColor(Color.ORANGE)
                .setTitle("Würfel-Bot Hilfe")
                .setDescription("Der Bot kann mit dem Befehl /r einen Würfelausdruck verarbeiten. " +
                        "Ein Würfelausdruck ist eine normale Rechnung mit Zuffallszahlen nach dem Muster xdy, " +
                        "wobei x die Zahl der Würfel und y die Seitenanzahl ist. " +
                        "Beispiel: 2d6 => zwei Würfel mit jeweils 6 Seiten\n\n" +
                        "Der Bot kann nicht nur einzelne Würfe " +
                        "entscheiden, sondern auch Würfe in einer Rechnung berechnen. Beispiel: 1d6+2");
    }

    private static class DieRecord {
        private static final SecureRandom random = new SecureRandom();

        private final int position;
        private final String expression;
        private String calculation;
        private int result;

        public DieRecord(int position, String expression) throws NumberFormatException {
            this.position = position;
            this.expression = expression;
            parseResult();
        }

        private void parseResult() throws NumberFormatException {
            String[] split = expression.split("d");
            int count = Integer.parseInt(split[0]);
            int bound = Integer.parseInt(split[1]);

            synchronized (random) {
                this.result = 0;
                String[] calculationSteps = new String[count];

                for (int i = 0; i < count; i++) {
                    // + 1 because the upper bound of java.util.Random is exclusive
                    int nextNumber = random.nextInt(bound) + 1;
                    result += nextNumber;
                    calculationSteps[i] = String.valueOf(nextNumber);
                }

                this.calculation = String.join(" + ", calculationSteps);
            }
        }

        @Override
        public boolean equals(Object obj) {
            return obj.getClass().equals(DieRecord.class)
                    && ((DieRecord) obj).result == result
                    && ((DieRecord) obj).position == position
                    && ((DieRecord) obj).expression.equals(expression)
                    && ((DieRecord) obj).calculation.equals(calculation);
        }
    }

    private static class ExecutionException extends RuntimeException {
        public ExecutionException(String reason) {
            super(reason);
        }
    }
}
