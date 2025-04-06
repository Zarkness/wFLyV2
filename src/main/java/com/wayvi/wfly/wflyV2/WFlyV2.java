package com.wayvi.wfly.wflyV2;

import com.wayvi.wfly.wflyV2.api.FlyManager;
import com.wayvi.wfly.wflyV2.api.TimeFlyManager;
import com.wayvi.wfly.wflyV2.api.WflyApi;
import com.wayvi.wfly.wflyV2.handlers.CustomMessageHandler;
import com.wayvi.wfly.wflyV2.pluginhook.cluescroll.FlyQuest;
import com.wayvi.wfly.wflyV2.commands.*;

import com.wayvi.wfly.wflyV2.listeners.FlyListener;
import com.wayvi.wfly.wflyV2.listeners.PvPListener;
import com.wayvi.wfly.wflyV2.managers.WConditionManager;
import com.wayvi.wfly.wflyV2.managers.fly.WFlyManager;
import com.wayvi.wfly.wflyV2.managers.PlaceholerapiManager;
import com.wayvi.wfly.wflyV2.managers.fly.WTimeFlyManager;
import com.wayvi.wfly.wflyV2.services.DatabaseService;
import com.wayvi.wfly.wflyV2.util.*;
import fr.maxlego08.sarah.RequestHelper;
import fr.traqueur.commands.api.CommandManager;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;

public final class WFlyV2 extends JavaPlugin {
    private TimeFormatTranslatorUtil timeFormatTranslatorUtil;
    private boolean isStartup = false;

    @Override
    public void onEnable() {

        WflyApi.inject(this);

        // INIT METRICS FOR BSTATS
        Metrics metrics = new Metrics(this, 24609);

        // INIT DATABASE
        DatabaseService databaseService = new DatabaseService(this);
        databaseService.initializeDatabase();

        // CONFIGS
        ConfigUtil configUtil = new ConfigUtil(this);
        configUtil.createCustomConfig();

        // INIT miniMessageSupport
        ColorSupportUtil miniMessageSupportUtil = new ColorSupportUtil();

        PlaceholerapiManager placeholerapiManager = new PlaceholerapiManager(this, configUtil);
        placeholerapiManager.checkPlaceholderAPI();
        placeholerapiManager.initialize();

        // INIT RequestHelper
        RequestHelper requestHelper = new RequestHelper(databaseService.getConnection(), this.getLogger()::info);

        this.timeFormatTranslatorUtil = new TimeFormatTranslatorUtil(configUtil);

        // INIT ConditionsManager
        WConditionManager conditionManager = new WConditionManager(configUtil);
        conditionManager.checkCanFly();
        WflyApi.inject(conditionManager);

        //INIT PvPListener
        PvPListener pvpListener = new PvPListener( configUtil);

        // INIT FlyManager
        FlyManager flyManager = new WFlyManager(this, requestHelper, configUtil);
        WflyApi.inject(flyManager);

        // INIT FlyQuest

        if (getServer().getPluginManager().isPluginEnabled("ClueScrolls")) {
            FlyQuest flyQuest = new FlyQuest(this);
            flyQuest.initializeFlyClue();
        } else {
            getLogger().info("ClueScrolls is not enabled: Skipping ClueScrolls integration");
        }

        // INIT TimeFlyManager
        TimeFlyManager  timeFlyManager = new WTimeFlyManager(requestHelper, configUtil);
        WflyApi.inject(timeFlyManager);
        try {
            timeFlyManager.decrementTimeRemaining();
            timeFlyManager.saveFlyTimes();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        // COMMANDS
        CommandManager<WFlyV2> commandManager = new CommandManager<>(this);
        commandManager.setDebug(false);
        commandManager.registerCommand(new ReloadCommand(this, configUtil, pvpListener, conditionManager));
        commandManager.registerCommand(new FlyCommand(this, configUtil, conditionManager, pvpListener));
        commandManager.registerCommand(new FlySpeedCommand(this, flyManager));
        commandManager.registerCommand(new AddTimeCommand(this, configUtil));
        commandManager.registerCommand(new ResetTimeCommand(this, configUtil));
        commandManager.registerCommand(new RemoveTimeCommand(this, configUtil));
        commandManager.setMessageHandler(new CustomMessageHandler(configUtil));
        commandManager.registerCommand(new FlyHelpCommand(this));

        // LISTENER
        getServer().getPluginManager().registerEvents(new FlyListener(this, flyManager, requestHelper, conditionManager, configUtil), this);
        getServer().getPluginManager().registerEvents(new PvPListener(configUtil), this);

        new VersionCheckerUtil(this, 118465).getLatestVersion(version -> {
            if (this.getDescription().getVersion().equalsIgnoreCase(version)) {
                this.getLogger().info("Plugin is up to date");
            } else {
                this.getLogger().info("Plugin has an update");
            }
        });
        getLogger().info("Plugin enabled");
        Bukkit.getScheduler().runTaskLater(this, () -> isStartup = true, 40L);
    }

    @Override
    public void onDisable() {
        getLogger().info("Plugin disabled");
        WflyApi.get().getTimeFlyManager().SaveFlyTimeOnDisable();
    }

    public boolean isStartup() {
        return this.isStartup;
    }

    public TimeFormatTranslatorUtil getTimeFormatTranslatorUtil() {
        return this.timeFormatTranslatorUtil;
    }
}
