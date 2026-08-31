# The Emerald Standard

A lightweight passive-investing and villager-economy mod for Minecraft 26.2, targeting Fabric and NeoForge.

## V1.0.0 scope
- Persistent deterministic Villager Exchange economy with bull markets, booms, stagnation, recessions, crashes, and recoveries.
- VILX broad-market index plus eight Minecraft-themed companies.
- Savings, CDs, fractional stock/index investing, and player-funded villager business loans.
- **Players can never borrow, hold negative balances, or go into debt.** Loans only mean the player gives emerald capital to villagers for a fixed term and later collects the investment.
- Offline catch-up. One Minecraft day is one economic day. Wall-clock time advances the economy while a world/server is offline.
- Valuable-resource exchange for emeralds: diamonds, gold, ancient debris, netherite, and emerald ores/resources.
- Server-authoritative accounts and micro-emerald accounting.

## V1 interface
V1 intentionally uses `/emerald` commands as the stable test interface while the Banker villager/workstation GUI is completed in the next UI milestone. Use `/emerald help` in-game.

Typical flow:
1. Put emeralds in your inventory and run `/emerald deposit 32`.
2. Check `/emerald market` and `/emerald portfolio`.
3. Invest with `/emerald buy VILX 20`, `/emerald savings deposit 10`, `/emerald cd open 10 90`, or `/emerald loan fund 10 180`.
4. Use `/emerald exchange diamond 1` to exchange supported valuables.

## Builds
Each loader has its own project in `fabric/` and `neoforge/`, while the deterministic economy lives in `common/`.

## License
MIT. See `LICENSE`.
