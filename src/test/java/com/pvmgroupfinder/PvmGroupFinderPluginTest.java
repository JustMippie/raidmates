package com.pvmgroupfinder;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class PvmGroupFinderPluginTest
{
    public static void main(String[] args) throws Exception
    {
        ExternalPluginManager.loadBuiltin(PvmGroupFinderPlugin.class);
        RuneLite.main(args);
    }
}
