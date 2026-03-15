package juggernighti.mc.home;

import com.google.gson.*;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.world.World;
import net.minecraft.util.Identifier;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class HomeMod implements ModInitializer {

    public static final String MOD_ID = "home";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final Path CONFIG_DIR = Path.of("config/juggernighti/home/");
    private static final Path SAVE_FILE = CONFIG_DIR.resolve("player_homes.json");

    // Store home locations keyed by player UUID
    private HashMap<String, String> playerHomes = new HashMap<>();

    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();


    @Override
    public void onInitialize() {
        try {
            LOGGER.info("juggernighti.mc.home.HomeMod started");
            loadPlayerHomes();

            // Register commands
            CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
                dispatcher.register(literal("home")
                        .then(literal("set")
                                .then(argument("homeName", StringArgumentType.string())
                                        .executes(this::setHomeCommand)))
                        .then(literal("remove")
                                .then(argument("homeName", StringArgumentType.string())
                                        .executes(this::removeHomeCommand)))
                        .then(argument("homeName", StringArgumentType.string())
                                .executes(this::homeCommand))
                        .executes(this::listCommand)
                );
            });
        } catch (Throwable e) {
            LOGGER.error("Fail:", e);
        }
    }

    // Command to set home
    private int setHomeCommand(CommandContext<ServerCommandSource> context) {
        try {
            ServerCommandSource source = context.getSource();
            String homeName = StringArgumentType.getString(context, "homeName");

            if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
                source.sendError(Text.literal("This command can only be used by a player.")
                        .styled(style -> style.withColor(Formatting.RED))
                );
                return 0;
            }

            //UUID playerId = player.getUuid();
            BlockPos playerPos = player.getBlockPos();
            String worldId = source.getWorld().getRegistryKey().getValue().toString();

            final String pos = playerPos.getX() + ";" + playerPos.getY() + ";" + playerPos.getZ() + ";" + worldId;

            // Get or create the player's homes map
            playerHomes.put(homeName, pos);
            player.sendMessage(Text.literal("Home '" + homeName + "' set at: " + playerPos.toShortString() + " (" + worldId + ")")
                    .styled(style -> style.withColor(Formatting.GOLD))
            );

            savePlayerHomes();
            return Command.SINGLE_SUCCESS;
        } catch (Throwable e) {
            LOGGER.error("Fail:", e);
        }
        return 2;
    }

    // Command to set home
    private int removeHomeCommand(CommandContext<ServerCommandSource> context) {
        try {
            ServerCommandSource source = context.getSource();
            String homeName = StringArgumentType.getString(context, "homeName");

            if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
                source.sendError(Text.literal("This command can only be used by a player.")
                        .styled(style -> style.withColor(Formatting.RED))
                );
                return 0;
            }

            String message = playerHomes.remove(homeName);
            savePlayerHomes();
            player.sendMessage(Text.literal("Deleted home:" + homeName + ", " + message)
                    .styled(style -> style.withColor(Formatting.GOLD))
            );
            return Command.SINGLE_SUCCESS;
        } catch (Throwable e) {
            LOGGER.error("Fail:", e);
        }
        return 2;
    }

    // Command to teleport home
    private int homeCommand(CommandContext<ServerCommandSource> context) {
        try {
            ServerCommandSource source = context.getSource();
            String homeName = StringArgumentType.getString(context, "homeName");

            if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
                source.sendError(Text.literal("This command can only be used by a player.")
                        .styled(style -> style.withColor(Formatting.RED))
                );
                return 0;
            }

            //UUID playerId = player.getUuid();
            // Check if the player has the specified home
            if (!playerHomes.containsKey(homeName)) {
                player.sendMessage(Text.literal("No home found with the name '"
                        + homeName + "'. Use /home set <name> first.")
                        .styled(style -> style.withColor(Formatting.RED))
                );
                return 0;
            }

            String posString = playerHomes.get(homeName);
            String[] posParts = posString.split(";");
            double x = Double.parseDouble(posParts[0]) + 0.5D;
            double y = Double.parseDouble(posParts[1]) + 1.5D;
            double z = Double.parseDouble(posParts[2]) + 0.5D;

            ServerWorld targetWorld = source.getWorld();
            if (posParts.length >= 4) {
                Identifier worldId = Identifier.of(posParts[3]);
                RegistryKey<World> worldKey = RegistryKey.of(RegistryKeys.WORLD, worldId);
                ServerWorld world = source.getServer().getWorld(worldKey);
                if (world != null) {
                    targetWorld = world;
                }
            }

            player.teleport(targetWorld, x, y, z, Set.of(), player.getYaw(), player.getPitch(), true);

            player.sendMessage(Text.literal("Teleported to home '" + homeName + "' (" + targetWorld.getRegistryKey().getValue().toString() + ")")
                    .styled(style -> style.withColor(Formatting.GOLD))
            );
            return Command.SINGLE_SUCCESS;
        } catch (Throwable e) {
            LOGGER.error("Fail:", e);
        }
        return 2;
    }

    private int listCommand(CommandContext<ServerCommandSource> context) {
        try {
            ServerCommandSource source = context.getSource();

            if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
                source.sendError(Text.literal("This command can only be used by a player.")
                        .styled(style -> style.withColor(Formatting.RED))
                );
                return 0;
            }

            Map<String, List<String>> groupedHomes = new TreeMap<>();
            for (Map.Entry<String, String> entry : playerHomes.entrySet()) {
                String homeName = entry.getKey();
                String[] posParts = entry.getValue().split(";");
                String dimension = "unknown";
                if (posParts.length >= 4) {
                    dimension = posParts[3];
                }
                groupedHomes.computeIfAbsent(dimension, k -> new ArrayList<>()).add(homeName);
            }

            StringBuilder message = new StringBuilder("--- Your Homes ---\n");
            for (Map.Entry<String, List<String>> entry : groupedHomes.entrySet()) {
                message.append("Dimension: ").append(entry.getKey()).append("\n");
                for (String home : entry.getValue()) {
                    message.append("  - ").append(home).append("\n");
                }
            }
            message.append("------------------");
            player.sendMessage(Text.literal(message.toString()), false);

            return Command.SINGLE_SUCCESS;
        } catch (Throwable e) {
            LOGGER.error("Fail:", e);
        }
        return 2;
    }


    // Save player homes to a file
    private void savePlayerHomes() {
        try {
            LOGGER.info(SAVE_FILE.toAbsolutePath().toString());

            // Ensure the directory exists
            if (!Files.exists(CONFIG_DIR)) {
                try {
                    Files.createDirectories(CONFIG_DIR);
                } catch (IOException e) {
                    LOGGER.error("Failed to created save file!", e);
                }
            }

            try (Writer writer = Files.newBufferedWriter(SAVE_FILE.toAbsolutePath())) {
                GSON.toJson(playerHomes, writer);
            } catch (IOException e) {
                LOGGER.error("Failed to save players!", e);
            }
        } catch (Throwable e) {
            LOGGER.error("Fail:", e);
        }
    }

    // Load player homes from a file
    private void loadPlayerHomes() {
        try {
            if (Files.exists(SAVE_FILE)) {
                HashMap<String, String> saveHomes = new HashMap<>();
                try (Reader reader = Files.newBufferedReader(SAVE_FILE)) {
                    saveHomes = GSON.fromJson(reader, HashMap.class);
                } catch (IOException e) {
                    LOGGER.error("Failed to load players!", e);
                }

                for (String home : saveHomes.keySet()) {
                    String values = saveHomes.get(home);
                    playerHomes.put(home, values);
                }
            }
        } catch (Throwable e) {
            LOGGER.error("Fail:", e);
        }
    }


}
