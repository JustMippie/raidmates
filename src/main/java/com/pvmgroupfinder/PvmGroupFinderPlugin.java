package com.pvmgroupfinder;

import com.google.inject.Provides;
import com.pvmgroupfinder.api.GroupFinderClient;
import com.pvmgroupfinder.model.CreateListingRequest;
import com.pvmgroupfinder.model.ChatMessage;
import com.pvmgroupfinder.model.GroupListing;
import com.pvmgroupfinder.model.JoinRequest;
import java.awt.Color;
import java.security.SecureRandom;
import java.util.Base64;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;
import javax.inject.Inject;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineEvent;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.FlashNotification;
import net.runelite.client.config.Notification;
import net.runelite.client.config.NotificationSound;
import net.runelite.client.config.RequestFocusType;
import net.runelite.client.Notifier;
import net.runelite.client.game.WorldService;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;

@Slf4j
@PluginDescriptor(
    name = "RaidMates",
    description = "Find teammates for raids, bosses, and group PvM activities",
    tags = {"pvm", "group", "party", "raids", "cox", "tob", "toa", "nex", "yama"}
)
public class PvmGroupFinderPlugin extends Plugin
{
    private static final String INSTALLATION_ID = "installationId";
    private static final String INSTALLATION_SECRET = "installationSecret";
    private static final Notification JOIN_NOTIFICATION = Notification.ON
        .withInitialized(true)
        .withOverride(true)
        .withTray(true)
        .withTrayIconType(TrayIcon.MessageType.NONE)
        .withRequestFocus(RequestFocusType.OFF)
        .withSound(NotificationSound.OFF)
        .withVolume(0)
        .withTimeout(5000)
        .withGameMessage(false)
        .withFlash(FlashNotification.DISABLED)
        .withFlashColor(new Color(42, 132, 82))
        .withSendWhenFocused(true);

    @Inject private Client client;
    @Inject private ClientToolbar clientToolbar;
    @Inject private ConfigManager configManager;
    @Inject private PvmGroupFinderConfig config;
    @Inject private GroupFinderClient api;
    @Inject private Notifier notifier;
    @Inject private WorldService worldService;

    private PvmGroupFinderPanel panel;
    private NavigationButton navigationButton;
    private UUID installationId;
    private String installationSecret;
    private Timer lobbyMonitorTimer;
    private Timer listingRefreshTimer;
    private String monitoredGroupId;
    private boolean lobbyMonitorInitialized;
    private boolean lobbyCheckInProgress;
    private boolean requestCheckInProgress;
    private volatile boolean sessionOpening;
    private volatile String sessionRsn;
    private String chatStreamGroupId;
    private String monitoredRequestListingId;
    private final Set<String> knownJoinRequestIds = new HashSet<>();

    @Provides
    PvmGroupFinderConfig provideConfig(ConfigManager manager)
    {
        return manager.getConfig(PvmGroupFinderConfig.class);
    }

    @Override
    protected void startUp()
    {
        installationId = loadInstallationId();
        installationSecret = loadInstallationSecret();
        api.configure(config.apiUrl());
        SwingUtilities.invokeLater(() ->
        {
            panel = new PvmGroupFinderPanel(
                this::refresh,
                this::createListing,
                this::requestJoin,
                this::showIncomingRequests,
                this::acceptRequest,
                this::rejectRequest,
                this::showMyGroup,
                this::closeGroup,
                this::leaveGroup,
                this::setReady,
                this::sendChatMessage,
                this::refreshChat,
                this::reportChatMessage,
                this::availableWorlds);
            navigationButton = NavigationButton.builder()
                .tooltip("RaidMates")
                .icon(createIcon())
                .priority(7)
                .panel(panel)
                .build();
            clientToolbar.addNavigation(navigationButton);
            lobbyMonitorTimer = new Timer(3000, e ->
            {
                ensureSessionForCurrentPlayer();
                checkForAcceptedGroup();
            });
            lobbyMonitorTimer.start();
            listingRefreshTimer = new Timer(10000, e -> refreshListingsSilently());
            listingRefreshTimer.start();
            connect();
        });
    }

    @Override
    protected void shutDown()
    {
        api.cancelAll();
        SwingUtilities.invokeLater(() ->
        {
            if (lobbyMonitorTimer != null) lobbyMonitorTimer.stop();
            if (listingRefreshTimer != null) listingRefreshTimer.stop();
            if (panel != null) panel.stopTimers();
            lobbyMonitorTimer = null;
            listingRefreshTimer = null;
            if (navigationButton != null) clientToolbar.removeNavigation(navigationButton);
            navigationButton = null;
            panel = null;
        });
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        if (!PvmGroupFinderConfig.GROUP.equals(event.getGroup())) return;
        api.cancelAll();
        sessionOpening = false;
        sessionRsn = null;
        api.configure(config.apiUrl());
        SwingUtilities.invokeLater(this::connect);
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        if (event.getGameState() == GameState.LOGGED_IN)
        {
            SwingUtilities.invokeLater(this::ensureSessionForCurrentPlayer);
        }
    }

    private void connect()
    {
        if (panel == null) return;
        if (!config.onlineEnabled())
        {
            panel.setStatus("Online service disabled");
            panel.showListings(java.util.Collections.emptyList());
            return;
        }
        if (sessionOpening) return;
        sessionOpening = true;
        api.cancelChatStream();
        chatStreamGroupId = null;
        String rsn = observedRsn();
        panel.setStatus("Connecting…");
        api.openSession(installationId, installationSecret, rsn)
            .thenRun(() ->
            {
                sessionRsn = rsn;
                sessionOpening = false;
                refresh(panel == null ? "ALL" : panel.selectedActivity());
            })
            .exceptionally(error ->
            {
                sessionOpening = false;
                showError("Connection failed");
                return null;
            });
    }

    private void ensureSessionForCurrentPlayer()
    {
        if (!config.onlineEnabled() || panel == null || sessionOpening) return;
        String rsn = observedRsn();
        if (rsn != null && !rsn.equals(sessionRsn)) connect();
    }

    private void refresh(String activity)
    {
        if (!config.onlineEnabled()) { showError("Enable the online service first"); return; }
        if (panel != null) panel.setStatus("Loading…");
        api.getListings(activity).thenAccept(items -> SwingUtilities.invokeLater(() ->
        {
            if (panel != null)
            {
                panel.showListings(items);
                panel.setStatus(items.size() + " open listing(s)");
            }
        })).exceptionally(error -> { showError("Unable to load listings"); return null; });
    }

    private void refreshListingsSilently()
    {
        if (!config.onlineEnabled() || panel == null || !panel.isListingsVisible()) return;
        String activity = panel.selectedActivity();
        api.getListings(activity).thenAccept(items -> SwingUtilities.invokeLater(() ->
        {
            if (panel != null && panel.isListingsVisible())
            {
                panel.showListings(items);
                panel.setStatus(items.size() + " open listing(s) · auto-refresh 10s");
            }
        })).exceptionally(error -> null);
    }

    private List<Integer> availableWorlds()
    {
        worldService.refresh();
        if (worldService.getWorlds() == null || worldService.getWorlds().getWorlds() == null)
        {
            return java.util.Collections.emptyList();
        }
        return worldService.getWorlds().getWorlds().stream()
            .map(world -> world.getId())
            .distinct()
            .sorted()
            .collect(Collectors.toList());
    }

    private void createListing(CreateListingRequest request)
    {
        String rsn = observedRsn();
        if (rsn == null) { showError("Log in before creating a listing"); return; }
        if (!config.onlineEnabled()) { showError("Enable the online service first"); return; }

        CreateListingRequest complete = CreateListingRequest.builder()
            .hostRsn(rsn).activity(request.getActivity()).teamSize(request.getTeamSize())
            .experienceKc(request.getExperienceKc()).role(request.getRole())
            .language(request.getLanguage()).region(request.getRegion()).note(request.getNote())
            .preferredWorld(request.getPreferredWorld()).useDiscord(request.isUseDiscord())
            .discordContact(request.getDiscordContact()).build();
        if (panel != null) panel.setStatus("Creating listing…");
        api.createListing(complete).thenAccept(created -> SwingUtilities.invokeLater(() ->
            {
                monitoredGroupId = created.getId();
                lobbyMonitorInitialized = true;
                monitoredRequestListingId = created.getId();
                knownJoinRequestIds.clear();
                requestCheckInProgress = false;
                refresh(panel == null ? "ALL" : panel.selectedActivity());
            }))
            .exceptionally(error -> { showError("Unable to create listing"); return null; });
    }

    private void requestJoin(GroupListing listing)
    {
        if (observedRsn() == null) { showError("Log in before requesting to join"); return; }
        if (!config.onlineEnabled()) { showError("Enable the online service first"); return; }
        if (panel != null) panel.setStatus("Sending join request…");
        api.requestJoin(listing.getId(), listing.getRole(), listing.getNote(),
                listing.getExperienceKc() == null ? 0 : listing.getExperienceKc())
            .thenRun(() -> SwingUtilities.invokeLater(() ->
            {
                if (panel != null) panel.setStatus("Join request sent");
            }))
            .exceptionally(error -> { showError("Unable to send request"); return null; });
    }

    private void showIncomingRequests()
    {
        if (!config.onlineEnabled()) { showError("Enable the online service first"); return; }
        if (panel != null) panel.setStatus("Loading requests…");
        api.getIncomingRequests().thenAccept(requests -> SwingUtilities.invokeLater(() ->
        {
            if (panel != null)
            {
                knownJoinRequestIds.clear();
                requests.forEach(request -> knownJoinRequestIds.add(request.getId()));
                panel.showIncomingRequests(requests);
                panel.setStatus(requests.size() + " pending request(s)");
            }
        })).exceptionally(error -> { showError("Unable to load requests"); return null; });
    }

    private void acceptRequest(JoinRequest request)
    {
        if (panel != null) panel.setStatus("Accepting " + request.getRequesterRsn() + "…");
        api.acceptRequest(request.getId())
            .thenRun(this::showMyGroupAndOpen)
            .exceptionally(error -> { showError("Unable to accept request"); return null; });
    }

    private void rejectRequest(JoinRequest request)
    {
        if (panel != null) panel.setStatus("Rejecting " + request.getRequesterRsn() + "…");
        api.rejectRequest(request.getId())
            .thenRun(this::showIncomingRequests)
            .exceptionally(error -> { showError("Unable to reject request"); return null; });
    }

    private void showMyGroup()
    {
        if (!config.onlineEnabled()) { showError("Enable the online service first"); return; }
        if (panel != null) panel.setStatus("Loading your group…");
        api.getMyGroup().thenAccept(group -> SwingUtilities.invokeLater(() ->
        {
            if (panel != null)
            {
                panel.showMyGroup(group);
                panel.setStatus(group == null ? "No active group" : group.getStatus());
            }
        })).thenRun(this::refreshChat)
            .exceptionally(error -> { showError("Unable to load your group"); return null; });
    }

    private void showMyGroupAndOpen()
    {
        if (!config.onlineEnabled()) return;
        api.getMyGroup().thenAccept(group -> SwingUtilities.invokeLater(() ->
        {
            if (panel == null || group == null) return;
            monitoredGroupId = group.getId();
            lobbyMonitorInitialized = true;
            panel.showMyGroup(group);
            panel.setStatus(group.getStatus());
            ensureChatStream(group);
            if (config.autoOpenLobby() && navigationButton != null)
            {
                clientToolbar.openPanel(navigationButton);
            }
            refreshChat();
        })).exceptionally(error -> { showError("Unable to open group lobby"); return null; });
    }

    private void checkForAcceptedGroup()
    {
        if (!config.onlineEnabled() || panel == null || lobbyCheckInProgress) return;
        lobbyCheckInProgress = true;
        api.getMyGroup().thenAccept(group -> SwingUtilities.invokeLater(() ->
        {
            String groupId = group == null ? null : group.getId();
            String previousGroupId = monitoredGroupId;
            boolean newlyAccepted = group != null
                && !group.isHost()
                && groupId != null
                && (!lobbyMonitorInitialized || !groupId.equals(monitoredGroupId));
            boolean groupEnded = lobbyMonitorInitialized
                && previousGroupId != null
                && groupId == null;
            monitoredGroupId = groupId;
            lobbyMonitorInitialized = true;
            lobbyCheckInProgress = false;
            if (group != null && panel.isLobbyVisible())
            {
                panel.showMyGroup(group);
            }
            checkIncomingRequests(group);
            ensureChatStream(group);
            if (groupEnded)
            {
                refresh(panel.selectedActivity());
                return;
            }
            if (newlyAccepted && config.autoOpenLobby())
            {
                panel.showMyGroup(group);
                panel.setStatus(group.getStatus());
                if (navigationButton != null) clientToolbar.openPanel(navigationButton);
                refreshChat();
            }
        })).exceptionally(error ->
        {
            SwingUtilities.invokeLater(() -> lobbyCheckInProgress = false);
            return null;
        });
    }

    private void checkIncomingRequests(GroupListing group)
    {
        if (group == null || !group.isHost())
        {
            monitoredRequestListingId = null;
            knownJoinRequestIds.clear();
            requestCheckInProgress = false;
            return;
        }
        if (!group.getId().equals(monitoredRequestListingId))
        {
            monitoredRequestListingId = group.getId();
            knownJoinRequestIds.clear();
            requestCheckInProgress = false;
        }
        if (requestCheckInProgress) return;
        requestCheckInProgress = true;
        String listingId = group.getId();
        api.getIncomingRequests().thenAccept(requests -> SwingUtilities.invokeLater(() ->
        {
            requestCheckInProgress = false;
            if (panel == null || !listingId.equals(monitoredRequestListingId)) return;
            List<JoinRequest> newRequests = requests.stream()
                .filter(request -> !knownJoinRequestIds.contains(request.getId()))
                .collect(Collectors.toList());
            knownJoinRequestIds.clear();
            requests.forEach(request -> knownJoinRequestIds.add(request.getId()));
            if (newRequests.isEmpty()) return;

            if (config.notifyJoinRequests())
            {
                newRequests.forEach(request ->
                    notifier.notify(JOIN_NOTIFICATION,
                        "RaidMates: New join request from " + request.getRequesterRsn()));
            }
            if (config.joinRequestSound())
            {
                newRequests.forEach(request -> CompletableFuture.runAsync(PvmGroupFinderPlugin::playJoinSound));
            }
            if (config.autoOpenRequests())
            {
                panel.showIncomingRequests(requests);
                panel.setStatus(requests.size() + " pending request(s)");
                if (navigationButton != null) clientToolbar.openPanel(navigationButton);
            }
        })).exceptionally(error ->
        {
            SwingUtilities.invokeLater(() -> requestCheckInProgress = false);
            return null;
        });
    }

    private static void playJoinSound()
    {
        try (InputStream resource = PvmGroupFinderPlugin.class.getResourceAsStream("/raidmates-join.wav"))
        {
            if (resource == null) return;
            try (AudioInputStream audio = AudioSystem.getAudioInputStream(new BufferedInputStream(resource)))
            {
                Clip clip = AudioSystem.getClip();
                clip.addLineListener(event ->
                {
                    if (event.getType() == LineEvent.Type.STOP) clip.close();
                });
                clip.open(audio);
                clip.start();
            }
        }
        catch (Exception error)
        {
            log.debug("Unable to play RaidMates join sound", error);
        }
    }

    private void closeGroup(GroupListing group)
    {
        if (panel != null) panel.setStatus("Closing group…");
        api.closeListing(group.getId()).thenRun(this::returnToListings)
            .exceptionally(error -> { showError("Unable to close group"); return null; });
    }

    private void leaveGroup(GroupListing group)
    {
        if (panel != null) panel.setStatus("Leaving group…");
        api.leaveGroup(group.getId()).thenRun(this::returnToListings)
            .exceptionally(error -> { showError("Unable to leave group"); return null; });
    }

    private void returnToListings()
    {
        SwingUtilities.invokeLater(() ->
        {
            monitoredGroupId = null;
            lobbyMonitorInitialized = true;
            monitoredRequestListingId = null;
            knownJoinRequestIds.clear();
            requestCheckInProgress = false;
            api.cancelChatStream();
            chatStreamGroupId = null;
            if (panel != null) refresh(panel.selectedActivity());
        });
    }

    private void ensureChatStream(GroupListing group)
    {
        if (group == null)
        {
            api.cancelChatStream();
            chatStreamGroupId = null;
            return;
        }
        if (group.getId().equals(chatStreamGroupId)) return;

        api.cancelChatStream();
        chatStreamGroupId = group.getId();
        String expectedGroupId = group.getId();
        api.openChatStream(
            message -> SwingUtilities.invokeLater(this::refreshChat),
            () -> SwingUtilities.invokeLater(() ->
            {
                if (expectedGroupId.equals(chatStreamGroupId)) chatStreamGroupId = null;
            }));
    }

    private void setReady(boolean ready)
    {
        if (panel != null) panel.setStatus(ready ? "Marking you ready…" : "Updating ready status…");
        api.setReady(ready).thenRun(this::showMyGroup)
            .exceptionally(error -> { showError("Unable to update ready status"); return null; });
    }

    private void refreshChat()
    {
        if (!config.onlineEnabled() || panel == null) return;
        api.getChatMessages().thenAccept(messages -> SwingUtilities.invokeLater(() ->
        {
            if (panel != null) panel.showChatMessages(messages);
        })).exceptionally(error -> null);
    }

    private void sendChatMessage(String message)
    {
        if (!config.onlineEnabled()) { showError("Enable the online service first"); return; }
        api.sendChatMessage(message)
            .thenRun(this::refreshChat)
            .exceptionally(error -> { showError("Unable to send chat message"); return null; });
    }

    private void reportChatMessage(ChatMessage message, String reason, String details)
    {
        api.reportChatMessage(message.getId(), reason, details)
            .thenRun(() -> SwingUtilities.invokeLater(() ->
            {
                if (panel != null) panel.setStatus("Message reported to RaidMates moderators");
            }))
            .exceptionally(error -> { showError("Unable to report message"); return null; });
    }

    private String observedRsn()
    {
        return client.getGameState() == GameState.LOGGED_IN && client.getLocalPlayer() != null
            ? client.getLocalPlayer().getName() : null;
    }

    private UUID loadInstallationId()
    {
        String value = configManager.getConfiguration(PvmGroupFinderConfig.GROUP, INSTALLATION_ID);
        try { return value == null ? createInstallationId() : UUID.fromString(value); }
        catch (IllegalArgumentException ignored) { return createInstallationId(); }
    }

    private String loadInstallationSecret()
    {
        String value = configManager.getConfiguration(PvmGroupFinderConfig.GROUP, INSTALLATION_SECRET);
        if (value != null && value.matches("[A-Za-z0-9_-]{43,128}"))
        {
            return value;
        }
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        String generated = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        configManager.setConfiguration(PvmGroupFinderConfig.GROUP, INSTALLATION_SECRET, generated);
        return generated;
    }

    private UUID createInstallationId()
    {
        UUID value = UUID.randomUUID();
        configManager.setConfiguration(PvmGroupFinderConfig.GROUP, INSTALLATION_ID, value.toString());
        return value;
    }

    private void showError(String message)
    {
        log.debug(message);
        SwingUtilities.invokeLater(() -> { if (panel != null) panel.setStatus(message); });
    }

    private static BufferedImage createIcon()
    {
        try (InputStream stream = PvmGroupFinderPlugin.class.getResourceAsStream("/raidmates-icon.png"))
        {
            if (stream != null)
            {
                BufferedImage source = ImageIO.read(stream);
                BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
                Graphics2D graphics = image.createGraphics();
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                graphics.drawImage(source, 0, 0, 16, 16, null);
                graphics.dispose();
                return image;
            }
        }
        catch (IOException ignored)
        {
            // Fall back to a simple generated icon below.
        }
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(new Color(70, 160, 110));
        graphics.fillOval(1, 1, 8, 8);
        graphics.fillOval(7, 5, 8, 8);
        graphics.dispose();
        return image;
    }
}
