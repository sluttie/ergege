package com.example.servermessagelogger;

import net.kyori.adventure.text.Component;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerListPingEvent;
import org.bukkit.event.server.ServerMessageEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

public final class ServerMessageLogger extends JavaPlugin implements Listener {

    private Connection connection;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        connectToDatabase();
        getServer().getPluginManager().registerEvents(this, this);
    }

    private void connectToDatabase() {
        String url = getConfig().getString("database.url");
        String username = getConfig().getString("database.username");
        String password = getConfig().getString("database.password");

        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection(url, username, password);
            createTableIfNotExists();
        } catch (ClassNotFoundException | SQLException e) {
            getLogger().severe("Failed to connect to database!");
            e.printStackTrace();
        }
    }

    private void createTableIfNotExists() throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS messages (message TEXT, count INTEGER DEFAULT 0)";
        PreparedStatement statement = connection.prepareStatement(sql);
        statement.execute();
        statement.close();
    }

    @EventHandler
    public void onServerMessage(ServerMessageEvent event) {
        String message = event.getMessage();
        if (message != null && !message.isEmpty()) {
            try {
                saveMessage(message);
            } catch (SQLException e) {
                getLogger().severe("Failed to save message to database!");
                e.printStackTrace();
            }
        }
    }

    private void saveMessage(String message) throws SQLException {
        String sql = "INSERT INTO messages (message, count) VALUES (?, 1) ON DUPLICATE KEY UPDATE count = count + 1";
        PreparedStatement statement = connection.prepareStatement(sql);
        statement.setString(1, message);
        statement.executeUpdate();
        statement.close();
    }

    @Override
    public boolean onCommand(CommandSender sender, org.bukkit.command.Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("gettopwords")) {
            try {
                Map<String, Integer> wordCounts = getWordCounts();
                Map<String, Integer> sortedWords = wordCounts.entrySet().stream()
                        .sorted(Map.Entry.comparingByValue((a, b) -> b - a))
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (oldValue, newValue) -> oldValue, LinkedHashMap::new));
                sender.sendMessage(Component.text("Top 10 Most Used Words:"));
                int count = 0;
                for (Map.Entry<String, Integer> entry : sortedWords.entrySet()) {
                    sender.sendMessage(Component.text(count + 1 + ". " + entry.getKey() + " - " + entry.getValue()));
                    count++;
                    if (count == 10) {
                        break;
                    }
                }
            } catch (SQLException e) {
                sender.sendMessage(Component.text("Error retrieving word counts!"));
                e.printStackTrace();
            }
            return true;
        }
        return false;
    }

    private Map<String, Integer> getWordCounts() throws SQLException {
        String sql = "SELECT message, count FROM messages";
                PreparedStatement statement = connection.prepareStatement(sql);
        ResultSet results = statement.executeQuery();
        Map<String, Integer> wordCounts = new HashMap<>();
        while (results.next()) {
            String message = results.getString("message");
            String[] words = message.split(" ");
            for (String word : words) {
                word = word.toLowerCase(); // Convert to lowercase for case-insensitive counting
                wordCounts.put(word, wordCounts.getOrDefault(word, 0) + 1);
            }
        }
        results.close();
        statement.close();
        return wordCounts;
    }

    @Override
    public void onDisable() {
        try {
            if (connection != null) {
                connection.close();
            }
        } catch (SQLException e) {
            getLogger().warning("Failed to close database connection!");
            e.printStackTrace();
        }
    }
}
