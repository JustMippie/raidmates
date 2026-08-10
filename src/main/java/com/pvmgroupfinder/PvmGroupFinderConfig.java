package com.pvmgroupfinder;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(PvmGroupFinderConfig.GROUP)
public interface PvmGroupFinderConfig extends Config
{
    String GROUP = "pvmgroupfinder";

    @ConfigItem(
        keyName = "onlineEnabled",
        name = "Enable online service",
        description = "Connect to the external RaidMates server. Sends a random installation ID, "
            + "your locally observed character name, manually submitted listings and join requests, "
            + "group state, lobby chat, reports, and technical connection data. "
            + "Privacy: https://api.raidmates.nl/privacy"
    )
    default boolean onlineEnabled()
    {
        return false;
    }

    @ConfigItem(
        keyName = "autoOpenLobby",
        name = "Automatically open lobby",
        description = "Open the RaidMates group lobby when your join request is accepted"
    )
    default boolean autoOpenLobby()
    {
        return true;
    }

    @ConfigItem(
        keyName = "autoOpenRequests",
        name = "Automatically open requests",
        description = "Open the RaidMates requests tab when a new join request arrives"
    )
    default boolean autoOpenRequests()
    {
        return true;
    }

    @ConfigItem(
        keyName = "notifyJoinRequests",
        name = "Join request notifications",
        description = "Show a RuneLite notification when a new join request arrives"
    )
    default boolean notifyJoinRequests()
    {
        return true;
    }

    @ConfigItem(
        keyName = "joinRequestSound",
        name = "RaidMates join sound",
        description = "Play the RaidMates chime for every new join request"
    )
    default boolean joinRequestSound()
    {
        return true;
    }

    default String apiUrl()
    {
        return "https://api.raidmates.nl";
    }
}
