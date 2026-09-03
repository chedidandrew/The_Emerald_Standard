# The Emerald Standard 0.3.0-beta.4

Beta.4 adds a one-command diagnostic flight recorder for hands-on mod testing.

## Use

Run `/emerald debug` as an operator. The command starts a full five-minute capture of the market, initiating player account, nearest village, development projects, settlers, GUI actions, persistence state, validation results, and capture performance. Run the same command again to stop early. Optional `/emerald debug mark` creates a numbered moment marker.

The completed report is packaged as a ZIP under the world's `data/the_emerald_standard_debug` directory. Interrupted captures are recovered on the next server start.

## Privacy

Reports exclude the private economy seed, world seed, chat, server addresses, and unrelated player accounts.

## Compatibility

- Minecraft 26.2
- Fabric Loader 0.19.3+ and Fabric API 0.158.0+26.2+
- NeoForge 26.2.0.72+
- Java 25

This remains a beta. The recorder is intended to make the remaining visual, multiplayer, terrain, recovery, and balance tests easy to share and diagnose.
