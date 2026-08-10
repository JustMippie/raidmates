# RaidMates

RaidMates is an opt-in RuneLite group finder for Old School RuneScape raids,
group bosses, and other PvM activities. Players create and browse listings,
request to join, and meet their accepted group in a private lobby.

RaidMates does not automate gameplay, click in the game, alter menu actions, or
claim to verify ownership of a RuneScape name.

## Features

- Listings for raids, group bosses, God Wars, and Wilderness activities.
- Filters for activity, team size, experience/KC, role, language, and region.
- Optional preferred world, visible only to accepted group members.
- Optional Discord contact; Discord is not required.
- Join requests with individual notifications and a RaidMates sound.
- Private accepted-group lobby with members, roles, ready status, and chat.
- Automatic listing refresh and instant chat updates with polling fallback.
- Chat reports with moderator review, temporary mutes, and service bans.
- Two-player limits for duo-only activities such as Royal Titans and Yama.
- A prominent risk warning for Wilderness activities.

## External service and privacy

The online service is disabled until the user explicitly enables it in the
plugin configuration. Enabling it connects to `https://api.raidmates.nl` and
sends a random installation ID, a locally observed character name, manually
submitted listings and join requests, group state, lobby chat, reports, and
technical connection data.

Each installation has a separate random secret stored in local RuneLite
configuration. The service stores only a one-way hash of that secret. RaidMates
never requests or stores Jagex passwords, bank PINs, authenticator codes, or
account recovery information.

- [Privacy Policy](https://api.raidmates.nl/privacy)
- [Community Rules](https://api.raidmates.nl/rules)
- Support, privacy requests, and moderation appeals:
  [support@raidmates.nl](mailto:support@raidmates.nl)

## Safety

RuneScape names shown by RaidMates are observed locally by the plugin and are not
cryptographically verified by Jagex. Never share account credentials or bring
items into the Wilderness that you are not prepared to lose. RaidMates cannot
guarantee another player's identity or intentions.

Users must follow the current Jagex, Old School RuneScape, and RuneLite rules.
RaidMates may not be used for cheating, real-world trading, paid account
services, scams, phishing, gambling, or harassment.

## Development

Requirements:

- Java 11
- A current RuneLite development environment

Import this directory as a Gradle project and run:

```text
gradlew.bat test
gradlew.bat run
```

The `runSecond` task starts a second development client with an isolated user
home for local two-account testing. Never commit RuneLite or Jagex launcher
credentials to this repository.

## Reporting issues

Open a GitHub issue in this repository for reproducible bugs. Do not include
access tokens, installation secrets, `.env` contents, launcher credentials,
private chat messages, or other personal information in a public issue.

For abuse reports and privacy requests, email
[support@raidmates.nl](mailto:support@raidmates.nl).

## Independence notice

RaidMates is an independent community project and is not affiliated with,
endorsed by, or operated by Jagex or RuneLite. Old School RuneScape and related
marks belong to their respective owners.

## License

RaidMates is available under the BSD 2-Clause License. See [LICENSE](LICENSE).
