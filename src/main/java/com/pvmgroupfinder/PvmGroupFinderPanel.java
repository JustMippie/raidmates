package com.pvmgroupfinder;

import com.pvmgroupfinder.model.CreateListingRequest;
import com.pvmgroupfinder.model.ChatMessage;
import com.pvmgroupfinder.model.GroupListing;
import com.pvmgroupfinder.model.GroupMember;
import com.pvmgroupfinder.model.JoinRequest;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.DefaultListCellRenderer;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import net.runelite.api.ItemID;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.AsyncBufferedImage;

public class PvmGroupFinderPanel extends PluginPanel
{
    @FunctionalInterface
    public interface ReportChatAction
    {
        void report(ChatMessage message, String reason, String details);
    }

    private static final Color BACKGROUND = new Color(14, 22, 18);
    private static final Color HEADER = new Color(10, 38, 27);
    private static final Color SURFACE = new Color(24, 39, 31);
    private static final Color SURFACE_ALT = new Color(30, 50, 39);
    private static final Color BRAND_GREEN = new Color(42, 132, 82);
    private static final Color BRAND_GOLD = new Color(232, 199, 105);
    private static final Color MUTED_TEXT = new Color(172, 188, 178);
    private static final Color DANGER = new Color(145, 58, 58);
    private final Consumer<String> refreshAction;
    private final Consumer<CreateListingRequest> createAction;
    private final Consumer<GroupListing> joinAction;
    private final Runnable inboxAction;
    private final Consumer<JoinRequest> acceptAction;
    private final Consumer<JoinRequest> rejectAction;
    private final Runnable myGroupAction;
    private final Consumer<GroupListing> closeGroupAction;
    private final Consumer<GroupListing> leaveGroupAction;
    private final Consumer<Boolean> readyAction;
    private final Consumer<String> sendChatAction;
    private final Runnable refreshChatAction;
    private final ReportChatAction reportChatAction;
    private final Supplier<List<Integer>> worldSupplier;
    private final ItemManager itemManager;
    private final Map<Integer, ImageIcon> activityIcons = new HashMap<>();
    private final JPanel chatMessages = new JPanel();
    private final JTextField chatInput = new JTextField();
    private final Timer chatTimer;
    private final JPanel listings = new JPanel();
    private final JPanel content = new JPanel(new BorderLayout());
    private final JScrollPane listingsScroll = new JScrollPane(listings);
    private final JPanel chatPanel;
    private final JLabel status = new JLabel("Online service disabled");
    private final JComboBox<ActivityOption> filter = new JComboBox<>(ActivityOption.values());
    private boolean lobbyVisible;
    private boolean listingsVisible;

    public PvmGroupFinderPanel(Consumer<String> refreshAction,
                               Consumer<CreateListingRequest> createAction,
                               Consumer<GroupListing> joinAction,
                               Runnable inboxAction,
                               Consumer<JoinRequest> acceptAction,
                               Consumer<JoinRequest> rejectAction,
                               Runnable myGroupAction,
                               Consumer<GroupListing> closeGroupAction,
                               Consumer<GroupListing> leaveGroupAction,
                               Consumer<Boolean> readyAction,
                               Consumer<String> sendChatAction,
                               Runnable refreshChatAction,
                               ReportChatAction reportChatAction,
                               Supplier<List<Integer>> worldSupplier,
                               ItemManager itemManager)
    {
        this.refreshAction = refreshAction;
        this.createAction = createAction;
        this.joinAction = joinAction;
        this.inboxAction = inboxAction;
        this.acceptAction = acceptAction;
        this.rejectAction = rejectAction;
        this.myGroupAction = myGroupAction;
        this.closeGroupAction = closeGroupAction;
        this.leaveGroupAction = leaveGroupAction;
        this.readyAction = readyAction;
        this.sendChatAction = sendChatAction;
        this.refreshChatAction = refreshChatAction;
        this.reportChatAction = reportChatAction;
        this.worldSupplier = worldSupplier;
        this.itemManager = itemManager;
        this.chatTimer = new Timer(3000, e -> this.refreshChatAction.run());
        this.chatPanel = buildChatPanel();
        setLayout(new BorderLayout());
        setBackground(BACKGROUND);
        add(buildHeader(), BorderLayout.NORTH);
        content.setBackground(BACKGROUND);
        content.add(listingsScroll, BorderLayout.CENTER);
        add(content, BorderLayout.CENTER);
        listings.setLayout(new BoxLayout(listings, BoxLayout.Y_AXIS));
        listings.setBackground(BACKGROUND);
        listings.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        listingsScroll.setBorder(BorderFactory.createEmptyBorder());
        listingsScroll.getViewport().setBackground(BACKGROUND);
        configureActivityCombo(filter);
        showListings(java.util.Collections.emptyList());
    }

    private JPanel buildHeader()
    {
        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setBackground(HEADER);
        header.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 2, 0, BRAND_GOLD),
            BorderFactory.createEmptyBorder(10, 10, 10, 10)));

        JLabel title = new JLabel("RaidMates");
        title.setForeground(BRAND_GOLD);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        JLabel subtitle = new JLabel("Find your next PvM team");
        subtitle.setForeground(MUTED_TEXT);
        subtitle.setFont(subtitle.getFont().deriveFont(11f));
        JPanel brand = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        brand.setBackground(HEADER);
        java.net.URL iconUrl = PvmGroupFinderPanel.class.getResource("/raidmates-icon.png");
        if (iconUrl != null) brand.add(new JLabel(new ImageIcon(iconUrl)));
        JPanel names = new JPanel();
        names.setLayout(new BoxLayout(names, BoxLayout.Y_AXIS));
        names.setBackground(HEADER);
        names.add(title);
        names.add(subtitle);
        brand.add(names);
        brand.setAlignmentX(LEFT_ALIGNMENT);
        filter.setMaximumSize(new Dimension(Integer.MAX_VALUE, filter.getPreferredSize().height));
        filter.setAlignmentX(LEFT_ALIGNMENT);
        filter.setBackground(SURFACE_ALT);
        filter.setForeground(Color.WHITE);

        JButton refresh = new JButton("Refresh");
        styleButton(refresh, false, false);
        refresh.addActionListener(e -> refreshAction.accept(selectedActivity()));
        JButton create = new JButton("Create Listing");
        styleButton(create, true, false);
        create.addActionListener(e -> showCreateDialog());
        JButton inbox = new JButton("Requests");
        styleButton(inbox, false, false);
        inbox.addActionListener(e -> inboxAction.run());
        JButton myGroup = new JButton("My Group");
        styleButton(myGroup, false, false);
        myGroup.addActionListener(e -> myGroupAction.run());
        JPanel buttons = new JPanel(new GridLayout(2, 2, 6, 4));
        buttons.setBackground(HEADER);
        buttons.setMaximumSize(new Dimension(Integer.MAX_VALUE, 64));
        buttons.setAlignmentX(LEFT_ALIGNMENT);
        buttons.add(refresh);
        buttons.add(create);
        buttons.add(inbox);
        buttons.add(myGroup);

        status.setForeground(BRAND_GOLD);
        status.setFont(status.getFont().deriveFont(Font.BOLD, 11f));
        status.setAlignmentX(LEFT_ALIGNMENT);
        JLabel identityNotice = new JLabel("<html><small>RSNs are client-observed, not Jagex-verified.</small></html>");
        identityNotice.setForeground(MUTED_TEXT);
        identityNotice.setAlignmentX(LEFT_ALIGNMENT);
        header.add(brand);
        header.add(Box.createVerticalStrut(8));
        header.add(filter);
        header.add(Box.createVerticalStrut(8));
        header.add(buttons);
        header.add(Box.createVerticalStrut(6));
        header.add(status);
        header.add(Box.createVerticalStrut(3));
        header.add(identityNotice);
        return header;
    }

    private void showCreateDialog()
    {
        JComboBox<ActivityOption> activity = new JComboBox<>(ActivityOption.withoutAll());
        configureActivityCombo(activity);
        JSpinner teamSize = new JSpinner(new SpinnerNumberModel(4, 2, 100, 1));
        JLabel teamSizeLabel = new JLabel("Team size");
        JSpinner kc = new JSpinner(new SpinnerNumberModel(0, 0, 1_000_000, 1));
        JComboBox<String> role = new JComboBox<>(new String[]{"ANY", "DPS", "TANK", "FREEZER", "SUPPORT", "LEARNER"});
        JComboBox<String> region = new JComboBox<>(new String[]{"EU", "US_EAST", "US_WEST", "AU", "OTHER"});
        JTextField language = new JTextField("EN");
        JTextField note = new JTextField();
        List<String> worldOptions = new ArrayList<>();
        worldOptions.add("Not set");
        worldSupplier.get().forEach(value -> worldOptions.add("World " + value));
        JComboBox<String> world = new JComboBox<>(worldOptions.toArray(new String[0]));
        JCheckBox useDiscord = new JCheckBox("Use Discord");
        JTextField discordContact = new JTextField();
        JLabel wildernessWarning = new JLabel("<html><b>Wilderness:</b> PvP and item loss are possible.</html>");
        wildernessWarning.setForeground(new Color(230, 100, 80));
        wildernessWarning.setVisible(false);
        discordContact.setEnabled(false);
        useDiscord.addActionListener(e -> discordContact.setEnabled(useDiscord.isSelected()));
        activity.addActionListener(e ->
        {
            ActivityOption selected = (ActivityOption) activity.getSelectedItem();
            wildernessWarning.setVisible(selected != null && selected.wilderness);
            int maximum = selected == null ? 100 : selected.maxTeamSize;
            SpinnerNumberModel model = (SpinnerNumberModel) teamSize.getModel();
            model.setMaximum(maximum);
            if ((Integer) model.getValue() > maximum) model.setValue(maximum);
            teamSizeLabel.setText(maximum == 100 ? "Team size" : "Team size (max " + maximum + ")");
        });

        JPanel form = new JPanel(new GridLayout(0, 2, 6, 6));
        form.add(new JLabel("Activity")); form.add(activity);
        form.add(teamSizeLabel); form.add(teamSize);
        form.add(new JLabel("Your KC")); form.add(kc);
        form.add(new JLabel("Role")); form.add(role);
        form.add(new JLabel("Region")); form.add(region);
        form.add(new JLabel("Language")); form.add(language);
        form.add(new JLabel("Note")); form.add(note);
        form.add(new JLabel("Preferred world")); form.add(world);
        form.add(new JLabel("Discord")); form.add(useDiscord);
        form.add(new JLabel("Discord contact")); form.add(discordContact);
        form.add(new JLabel("Risk warning")); form.add(wildernessWarning);

        if (JOptionPane.showConfirmDialog(this, form, "Create listing",
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION)
        {
            ActivityOption selected = (ActivityOption) activity.getSelectedItem();
            if (selected != null && selected.wilderness)
            {
                int confirmation = JOptionPane.showConfirmDialog(
                    this,
                    "This activity takes place in or requires travel through the Wilderness.\n"
                        + "Other players may attack you and you may lose items.\n"
                        + "RaidMates cannot prevent or reimburse deaths, scams, lures, or item loss.\n"
                        + "Never bring items you are not willing to lose.\n\n"
                        + "Continue and create this listing?",
                    "Wilderness risk warning",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);
                if (confirmation != JOptionPane.YES_OPTION) return;
            }
            Integer preferredWorld = null;
            String selectedWorld = (String) world.getSelectedItem();
            if (selectedWorld != null && selectedWorld.startsWith("World "))
            {
                preferredWorld = Integer.parseInt(selectedWorld.substring(6));
            }
            if (useDiscord.isSelected() && discordContact.getText().trim().isEmpty())
            {
                setStatus("Enter a Discord username or invite");
                return;
            }
            createAction.accept(CreateListingRequest.builder()
                .activity(selected == null ? "COX" : selected.id)
                .teamSize((Integer) teamSize.getValue())
                .experienceKc((Integer) kc.getValue())
                .role((String) role.getSelectedItem())
                .region((String) region.getSelectedItem())
                .language(language.getText().trim())
                .note(note.getText().trim())
                .preferredWorld(preferredWorld)
                .useDiscord(useDiscord.isSelected())
                .discordContact(useDiscord.isSelected() ? discordContact.getText().trim() : null)
                .build());
        }
    }

    public void showListings(List<GroupListing> items)
    {
        listingsVisible = true;
        lobbyVisible = false;
        chatTimer.stop();
        hideChatPanel();
        listings.removeAll();
        if (items.isEmpty())
        {
            JLabel empty = new JLabel("No open listings found");
            empty.setForeground(Color.GRAY);
            empty.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            listings.add(empty);
        }
        else
        {
            for (GroupListing listing : items)
            {
                listings.add(buildCard(listing));
                listings.add(Box.createVerticalStrut(6));
            }
        }
        listings.revalidate();
        listings.repaint();
    }

    private JPanel buildCard(GroupListing listing)
    {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        styleCard(card);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 150));
        JLabel heading = new JLabel(listing.getActivity() + " — " + listing.getHostRsn());
        heading.setText(activityLabel(listing.getActivity())
            + heading.getText().substring(listing.getActivity().length()));
        addActivityIcon(heading, listing.getActivity());
        heading.setForeground(BRAND_GOLD);
        heading.setFont(heading.getFont().deriveFont(Font.BOLD));
        JLabel details = new JLabel(listing.getCurrentSize() + "/" + listing.getTeamSize()
            + " · " + listing.getRegion() + " · " + listing.getLanguage());
        details.setForeground(MUTED_TEXT);
        card.add(heading);
        card.add(details);
        if (listing.getNote() != null && !listing.getNote().isEmpty())
        {
            JLabel note = new JLabel(listing.getNote());
            note.setForeground(MUTED_TEXT.darker());
            card.add(note);
        }
        JButton join = new JButton("Request to join");
        styleButton(join, true, false);
        join.addActionListener(e -> showJoinDialog(listing));
        card.add(Box.createVerticalStrut(5));
        card.add(join);
        return card;
    }

    private void showJoinDialog(GroupListing listing)
    {
        JComboBox<String> role = new JComboBox<>(new String[]{"ANY", "DPS", "TANK", "FREEZER", "SUPPORT", "LEARNER"});
        JSpinner experienceKc = new JSpinner(new SpinnerNumberModel(0, 0, 1_000_000, 1));
        JTextField message = new JTextField();
        JPanel form = new JPanel(new GridLayout(0, 2, 6, 6));
        form.add(new JLabel("Role")); form.add(role);
        form.add(new JLabel("KC / raids completed")); form.add(experienceKc);
        form.add(new JLabel("Message")); form.add(message);
        if (JOptionPane.showConfirmDialog(this, form, "Join " + listing.getHostRsn(),
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION)
        {
            listing.setRole((String) role.getSelectedItem());
            listing.setExperienceKc((Integer) experienceKc.getValue());
            listing.setNote(message.getText().trim());
            joinAction.accept(listing);
        }
    }

    public void showIncomingRequests(List<JoinRequest> requests)
    {
        listingsVisible = false;
        lobbyVisible = false;
        chatTimer.stop();
        hideChatPanel();
        listings.removeAll();
        if (requests.isEmpty())
        {
            JLabel empty = new JLabel("No pending requests");
            empty.setForeground(Color.GRAY);
            empty.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            listings.add(empty);
        }
        else
        {
            for (JoinRequest request : requests)
            {
                JPanel card = new JPanel();
                card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
                styleCard(card);
                card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 120));
                JLabel title = new JLabel(request.getRequesterRsn() + " — " + request.getRole()
                    + " — " + request.getExperienceKc() + " KC");
                title.setForeground(BRAND_GOLD);
                title.setFont(title.getFont().deriveFont(Font.BOLD));
                card.add(title);
                if (request.getMessage() != null && !request.getMessage().isEmpty())
                {
                    JLabel message = new JLabel(request.getMessage());
                    message.setForeground(MUTED_TEXT);
                    card.add(message);
                }
                JPanel actions = new JPanel(new GridLayout(1, 2, 5, 0));
                actions.setBackground(SURFACE);
                JButton accept = new JButton("Accept");
                styleButton(accept, true, false);
                accept.addActionListener(e -> acceptAction.accept(request));
                JButton reject = new JButton("Reject");
                styleButton(reject, false, true);
                reject.addActionListener(e -> rejectAction.accept(request));
                actions.add(accept);
                actions.add(reject);
                card.add(Box.createVerticalStrut(5));
                card.add(actions);
                listings.add(card);
                listings.add(Box.createVerticalStrut(6));
            }
        }
        listings.revalidate();
        listings.repaint();
    }

    public void showMyGroup(GroupListing group)
    {
        listingsVisible = false;
        lobbyVisible = group != null;
        chatTimer.stop();
        listings.removeAll();
        if (group == null)
        {
            hideChatPanel();
            JLabel empty = new JLabel("You are not in an active group");
            empty.setForeground(Color.GRAY);
            empty.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            listings.add(empty);
        }
        else
        {
            JPanel card = new JPanel();
            card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
            styleCard(card);
            card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 280));
            JLabel title = new JLabel(group.getActivity() + " — " + group.getCurrentSize() + "/" + group.getTeamSize());
            title.setText(activityLabel(group.getActivity())
                + title.getText().substring(group.getActivity().length()));
            addActivityIcon(title, group.getActivity());
            title.setForeground(BRAND_GOLD);
            title.setFont(title.getFont().deriveFont(Font.BOLD));
            card.add(title);
            JLabel worldLabel = new JLabel("World: " + (group.getPreferredWorld() == null ? "Not set" : group.getPreferredWorld()));
            worldLabel.setForeground(MUTED_TEXT);
            card.add(worldLabel);
            if (group.isUseDiscord())
            {
                JLabel discord = new JLabel("Discord: " + group.getDiscordContact());
                discord.setForeground(MUTED_TEXT);
                card.add(discord);
            }
            card.add(Box.createVerticalStrut(8));
            for (GroupMember member : group.getMembers())
            {
                JLabel memberLabel = new JLabel((member.isReady() ? "✓ " : "○ ")
                    + member.getRsn() + " — " + member.getRole()
                    + " — " + (member.getExperienceKc() == null ? 0 : member.getExperienceKc()) + " KC"
                    + (member.isHost() ? " (Host)" : ""));
                memberLabel.setForeground(member.isReady() ? new Color(80, 200, 120) : Color.LIGHT_GRAY);
                card.add(memberLabel);
            }
            JButton ready = new JButton(group.isReady() ? "Mark not ready" : "I'm ready");
            styleButton(ready, true, false);
            ready.addActionListener(e -> readyAction.accept(!group.isReady()));
            JButton action = new JButton(group.isHost() ? "Close group" : "Leave group");
            styleButton(action, false, true);
            action.addActionListener(e ->
            {
                if (group.isHost()) closeGroupAction.accept(group);
                else leaveGroupAction.accept(group);
            });
            card.add(Box.createVerticalStrut(5));
            card.add(ready);
            card.add(Box.createVerticalStrut(5));
            card.add(action);
            listings.add(card);
            showChatPanel();
            chatTimer.start();
        }
        listings.revalidate();
        listings.repaint();
    }

    private JPanel buildChatPanel()
    {
        JPanel chat = new JPanel();
        chat.setLayout(new BoxLayout(chat, BoxLayout.Y_AXIS));
        chat.setBackground(SURFACE);
        chat.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(2, 0, 0, 0, BRAND_GOLD),
            BorderFactory.createEmptyBorder(8, 8, 8, 8)));
        chat.setPreferredSize(new Dimension(0, 235));

        JLabel heading = new JLabel("Group lobby chat");
        heading.setForeground(BRAND_GOLD);
        heading.setFont(heading.getFont().deriveFont(Font.BOLD));
        JLabel notice = new JLabel("External chat — not endorsed or moderated by Jagex.");
        notice.setForeground(new Color(230, 170, 70));
        chatMessages.setLayout(new BoxLayout(chatMessages, BoxLayout.Y_AXIS));
        chatMessages.setBackground(SURFACE);

        JButton send = new JButton("Send");
        styleButton(send, true, false);
        send.addActionListener(e -> sendCurrentMessage());
        chatInput.addActionListener(e -> sendCurrentMessage());
        JPanel composer = new JPanel(new BorderLayout(5, 0));
        composer.setBackground(SURFACE);
        composer.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        composer.add(chatInput, BorderLayout.CENTER);
        composer.add(send, BorderLayout.EAST);

        chat.add(heading);
        chat.add(notice);
        chat.add(Box.createVerticalStrut(6));
        JScrollPane messageScroll = new JScrollPane(chatMessages);
        messageScroll.setPreferredSize(new Dimension(0, 150));
        messageScroll.setBorder(BorderFactory.createLineBorder(new Color(50, 78, 62)));
        messageScroll.getViewport().setBackground(SURFACE);
        chat.add(messageScroll);
        chat.add(Box.createVerticalStrut(6));
        chat.add(composer);
        return chat;
    }

    private void showChatPanel()
    {
        if (chatPanel.getParent() != content)
        {
            content.add(chatPanel, BorderLayout.SOUTH);
            content.revalidate();
            content.repaint();
        }
    }

    private void hideChatPanel()
    {
        if (chatPanel.getParent() == content)
        {
            content.remove(chatPanel);
            content.revalidate();
            content.repaint();
        }
    }

    private void sendCurrentMessage()
    {
        String message = chatInput.getText().trim();
        if (message.isEmpty()) return;
        if (message.length() > 240)
        {
            setStatus("Chat messages may contain up to 240 characters");
            return;
        }
        chatInput.setText("");
        sendChatAction.accept(message);
    }

    public void showChatMessages(List<ChatMessage> messages)
    {
        chatMessages.removeAll();
        if (messages.isEmpty())
        {
            JLabel empty = new JLabel("No messages yet");
            empty.setForeground(Color.GRAY);
            chatMessages.add(empty);
        }
        else
        {
            for (ChatMessage message : messages)
            {
                JPanel row = new JPanel(new BorderLayout(4, 0));
                row.setBackground(SURFACE_ALT);
                row.setBorder(BorderFactory.createEmptyBorder(4, 5, 4, 4));
                JLabel text = new JLabel(message.getSenderRsn() + ": " + message.getBody());
                text.setForeground(Color.WHITE);
                JButton report = new JButton("Report");
                styleButton(report, false, false);
                report.setToolTipText("Report this external chat message to RaidMates moderators");
                report.addActionListener(e ->
                {
                    String[] reasons = {"SPAM", "HARASSMENT", "OFFENSIVE", "PERSONAL_INFO", "OTHER"};
                    JComboBox<String> reason = new JComboBox<>(reasons);
                    JTextArea details = new JTextArea(4, 22);
                    details.setLineWrap(true);
                    details.setWrapStyleWord(true);
                    JPanel reportForm = new JPanel();
                    reportForm.setLayout(new BoxLayout(reportForm, BoxLayout.Y_AXIS));
                    reportForm.add(new JLabel("Reason:"));
                    reportForm.add(reason);
                    reportForm.add(Box.createVerticalStrut(8));
                    reportForm.add(new JLabel("Explain what happened (3-500 characters):"));
                    reportForm.add(new JScrollPane(details));
                    reportForm.add(Box.createVerticalStrut(8));
                    reportForm.add(new JLabel("All available messages from this player in this lobby will be included."));
                    int selected = JOptionPane.showConfirmDialog(
                        this,
                        reportForm,
                        "Report chat message",
                        JOptionPane.OK_CANCEL_OPTION,
                        JOptionPane.WARNING_MESSAGE);
                    if (selected == JOptionPane.OK_OPTION)
                    {
                        String explanation = details.getText().trim();
                        if (explanation.length() < 3 || explanation.length() > 500)
                        {
                            JOptionPane.showMessageDialog(
                                this,
                                "Please enter an explanation between 3 and 500 characters.",
                                "Report not sent",
                                JOptionPane.ERROR_MESSAGE);
                            return;
                        }
                        reportChatAction.report(message, reason.getSelectedItem().toString(), explanation);
                    }
                });
                row.add(text, BorderLayout.CENTER);
                row.add(report, BorderLayout.EAST);
                chatMessages.add(row);
                chatMessages.add(Box.createVerticalStrut(3));
            }
        }
        chatMessages.revalidate();
        chatMessages.repaint();
    }

    public void setStatus(String message)
    {
        status.setText(message);
    }

    public String selectedActivity()
    {
        ActivityOption selected = (ActivityOption) filter.getSelectedItem();
        return selected == null ? "ALL" : selected.id;
    }

    public boolean isLobbyVisible()
    {
        return lobbyVisible;
    }

    public boolean isListingsVisible()
    {
        return listingsVisible;
    }

    public void stopTimers()
    {
        chatTimer.stop();
    }

    private static void styleCard(JPanel card)
    {
        card.setBackground(SURFACE);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(48, 82, 63)),
            BorderFactory.createEmptyBorder(9, 9, 9, 9)));
    }

    private static void styleButton(JButton button, boolean primary, boolean danger)
    {
        Color background = danger ? DANGER : (primary ? BRAND_GREEN : SURFACE_ALT);
        Color foreground = primary || danger ? Color.WHITE : BRAND_GOLD;
        button.setBackground(background);
        button.setForeground(foreground);
        button.setFocusPainted(false);
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(primary ? BRAND_GREEN.brighter() : background.brighter()),
            BorderFactory.createEmptyBorder(4, 8, 4, 8)));
    }

    private void configureActivityCombo(JComboBox<ActivityOption> comboBox)
    {
        comboBox.setRenderer(new DefaultListCellRenderer()
        {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                           boolean isSelected, boolean cellHasFocus)
            {
                JLabel label = (JLabel) super.getListCellRendererComponent(
                    list, value, index, isSelected, cellHasFocus);
                ActivityOption activity = value instanceof ActivityOption ? (ActivityOption) value : null;
                label.setText(activity == null ? "" : activity.label);
                label.setIcon(getActivityIcon(activity));
                label.setIconTextGap(7);
                return label;
            }
        });
    }

    private ImageIcon getActivityIcon(ActivityOption activity)
    {
        if (activity == null || activity.iconItemId < 0)
        {
            return null;
        }

        return activityIcons.computeIfAbsent(activity.iconItemId, itemId ->
        {
            AsyncBufferedImage image = itemManager.getImage(itemId);
            image.onLoaded(() -> SwingUtilities.invokeLater(this::repaint));
            return new ImageIcon(image);
        });
    }

    private void addActivityIcon(JLabel label, String activityId)
    {
        ActivityOption activity = ActivityOption.findById(activityId);
        if (activity == null || activity.iconItemId < 0)
        {
            return;
        }

        itemManager.getImage(activity.iconItemId).addTo(label);
        label.setIconTextGap(7);
    }

    private static String activityLabel(String activityId)
    {
        ActivityOption activity = ActivityOption.findById(activityId);
        return activity == null ? activityId : activity.label;
    }

    private enum ActivityOption
    {
        ALL("ALL", "All activities", -1),
        COX("COX", "Chambers of Xeric", ItemID.TWISTED_BOW),
        TOB("TOB", "Theatre of Blood", ItemID.SCYTHE_OF_VITUR),
        TOA("TOA", "Tombs of Amascut", ItemID.TUMEKENS_SHADOW),
        NEX("NEX", "Nex", ItemID.NEXLING),
        NIGHTMARE("NIGHTMARE", "The Nightmare", ItemID.LITTLE_NIGHTMARE),
        YAMA("YAMA", "Yama", ItemID.DOM, 2),
        HUEYCOATL("HUEYCOATL", "The Hueycoatl", ItemID.HUBERTE),
        ROYAL_TITANS("ROYAL_TITANS", "Royal Titans", ItemID.BRAN, 2),
        CORPOREAL_BEAST("CORPOREAL_BEAST", "Corporeal Beast", ItemID.PET_DARK_CORE),
        GENERAL_GRAARDOR("GENERAL_GRAARDOR", "General Graardor", ItemID.PET_GENERAL_GRAARDOR),
        KREEARRA("KREEARRA", "Kree'arra", ItemID.PET_KREEARRA),
        KRIL_TSUTSAROTH("KRIL_TSUTSAROTH", "K'ril Tsutsaroth", ItemID.PET_KRIL_TSUTSAROTH),
        COMMANDER_ZILYANA("COMMANDER_ZILYANA", "Commander Zilyana", ItemID.PET_ZILYANA),
        DAGANNOTH_KINGS("DAGANNOTH_KINGS", "Dagannoth Kings", ItemID.PET_DAGANNOTH_PRIME),
        SCURRIUS("SCURRIUS", "Scurrius", ItemID.SCURRY),
        GIANT_MOLE("GIANT_MOLE", "Giant Mole", ItemID.BABY_MOLE),
        KALPHITE_QUEEN("KALPHITE_QUEEN", "Kalphite Queen", ItemID.KALPHITE_PRINCESS),
        SARACHNIS("SARACHNIS", "Sarachnis", ItemID.SRARACHA),
        GEMSTONE_CRAB("GEMSTONE_CRAB", "Gemstone Crab", ItemID.RAINBOW_CRAB),
        CALLISTO("CALLISTO", "[Wilderness] Callisto", ItemID.CALLISTO_CUB, true),
        VENENATIS("VENENATIS", "[Wilderness] Venenatis", ItemID.VENENATIS_SPIDERLING, true),
        VETION("VETION", "[Wilderness] Vet'ion", ItemID.VETION_JR, true),
        CHAOS_ELEMENTAL("CHAOS_ELEMENTAL", "[Wilderness] Chaos Elemental", ItemID.PET_CHAOS_ELEMENTAL, true),
        KING_BLACK_DRAGON("KING_BLACK_DRAGON", "[Wilderness travel] King Black Dragon", ItemID.PRINCE_BLACK_DRAGON, true),
        SCORPIA("SCORPIA", "[Wilderness] Scorpia", ItemID.SCORPIAS_OFFSPRING, true),
        REVENANT_MALEDICTUS("REVENANT_MALEDICTUS", "[Wilderness] Revenant maledictus", ItemID.CRAWS_BOW, true);

        private final String id;
        private final String label;
        private final int iconItemId;
        private final boolean wilderness;
        private final int maxTeamSize;
        ActivityOption(String id, String label, int iconItemId)
        {
            this(id, label, iconItemId, false, 100);
        }
        ActivityOption(String id, String label, int iconItemId, boolean wilderness)
        {
            this(id, label, iconItemId, wilderness, 100);
        }
        ActivityOption(String id, String label, int iconItemId, int maxTeamSize)
        {
            this(id, label, iconItemId, false, maxTeamSize);
        }
        ActivityOption(String id, String label, int iconItemId, boolean wilderness, int maxTeamSize)
        {
            this.id = id;
            this.label = label;
            this.iconItemId = iconItemId;
            this.wilderness = wilderness;
            this.maxTeamSize = maxTeamSize;
        }
        @Override public String toString() { return label; }
        static ActivityOption findById(String id)
        {
            if (id == null) return null;
            for (ActivityOption activity : values())
            {
                if (activity.id.equalsIgnoreCase(id)) return activity;
            }
            return null;
        }
        static ActivityOption[] withoutAll()
        {
            ActivityOption[] values = values();
            ActivityOption[] result = new ActivityOption[values.length - 1];
            System.arraycopy(values, 1, result, 0, result.length);
            return result;
        }
    }
}
