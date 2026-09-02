# Village Prosperity System

The Village Prosperity System connects the global Villager Exchange to persistent local village economies while keeping the mod lightweight. Villages are simulated as compact records. Physical villagers and structures are processed only when their chunks are loaded and players are nearby.

## Configuration modes

The economic simulation and visible world progression are independent world settings:

```properties
village_prosperity.simulation_enabled=true
village_prosperity.visual_progression_enabled=true
```

Supported combinations:

| Simulation | Visual progression | Behavior |
|---|---|---|
| On | On | Full prosperity simulation, market influence, settlers, and gradual construction |
| On | Off | Village economies and market fundamentals continue, but no prosperity structures or settlers are materialized |
| Off | On | Loaded villages can show lightweight visual activity and construction progress without changing market fundamentals |
| Off | Off | The original global market and banking experience only |

The visual option never force-loads chunks. Turning it off leaves completed structures intact and pauses future materialization.

## Stable village identity

Each managed village receives a private UUID associated with its dimension, original center, seed, and persisted bank region. Villagers carry the village UUID as a durable tag. Bankers, projects, incidents, structures, and prosperity data all reference that identity rather than relying only on a configurable map grid.

The first loaded census records beds, residents, professions, and nearby threats. Later scans update known residents without treating an unloaded or missing entity as dead. An explicit Minecraft death event is required before a resident is counted as a casualty.

## Simulated resources

Every village maintains bounded abstract values for:

- Population and housing capacity
- Food supply
- Material supply
- Treasury
- Prosperity
- Safety
- Farming output
- Mining output
- Trade output
- Development points

The system does not mine real terrain or duplicate physical ores. Miner, farmer, merchant, and builder activity is represented economically. Visible particles and work-site activity are presentation only.

## Development tiers

Villages progress through six stages:

| Tier | Name |
|---:|---|
| 0 | Hamlet |
| 1 | Village |
| 2 | Growing Village |
| 3 | Town |
| 4 | Prosperous Town |
| 5 | Regional Center |

Population, housing, prosperity, safety, treasury, and completed development projects determine the tier. Progress can reverse after severe collapse, but completed structures are never automatically deleted.

## Project queue

The beta starts with three development projects:

- Cottage: increases housing capacity
- Warehouse: improves resource storage and local production efficiency
- Mine Entrance: improves abstract mining output

Projects are approved by the simulation only when the village has enough population, supplies, safety, and development points. Offline catch-up advances project economics, then compresses long absences into a bounded queue instead of placing years of buildings during world loading.

When visual progression is enabled, one project at a time reserves a safe nearby lot. The server places only a configurable number of blocks at each construction interval. No project force-loads chunks, removes block entities, clears protected space, or replaces a site that has become occupied.

## Lightweight worker representation

Villagers do not run a complete offline AI economy. Loaded residents can visit ordinary workplaces and the current construction area while particles and sounds show activity. Production and project completion remain controlled by the server-side prosperity record, so deaths, pathfinding failures, and unloaded chunks cannot corrupt the economy.

## Village lifecycle

A village can be:

- Active
- Threatened
- Devastated
- Extinct
- Recovering
- Abandoned

Casualties reduce population, output, safety, prosperity, and project speed. A village with no living residents stops producing resources and approving projects. Existing structures, financial accounts, history, and the village identity remain intact.

### Hostile destruction

Pillager, raid, zombie, and other hostile casualties can move a village into a recoverable extinct state. After a safety cooldown, a loaded village with usable housing and no nearby threat may receive two settlers. Repeated collapses increase the delay and can suspend automatic recovery.

### Player-caused destruction

A player-caused extinction becomes Abandoned. It does not automatically spawn replacement residents and is excluded from market fundamentals to prevent stock-price manipulation. Restoration requires imported villagers, cured residents, or a funded restoration effort.

### Environmental and unknown deaths

Environmental incidents are recorded and reduce the local economy, but they use conservative market influence. A resident missing from a scan becomes Away after a grace period and later Emigrated. Missing residents are never assumed dead.

## Restoration

The Village page in the Banker dashboard shows the local lifecycle, population, housing, prosperity, safety, supplies, treasury, tier, current project, and construction backlog.

Players can contribute emeralds to village support. This is a local development contribution, not a loan and not a guaranteed investment return. An abandoned or extinct village becomes eligible for restoration after the configured target is met and the loaded-world safety and housing checks pass.

Player accounts remain world-level. A destroyed or unstaffed village can never erase cash, savings, investments, CDs, or villager business lending.

## Market connection

Active simulated villages contribute a small, capped fundamental factor to the global economy:

- Mining supports Deepdelve Mining and commodity supply
- Farming supports Golden Harvest Cooperative
- Trade and transportation support Nether Spice, Ender Freight, and Minecart Transit
- Safety supports Iron Golem Security
- Prosperity and development support Redstone Dynamics and Potionworks

The combined annual village contribution is capped so the global regime, market volatility, and company-specific events remain dominant. Player-caused destruction is excluded from the market calculation.

## Offline behavior

Offline progression updates data only. It does not create raids, kill villagers, force chunks, move entities, mine terrain, or instantly place structures. When a village is loaded again, its abstract state is already current and any missing physical projects enter the gradual construction queue.

This split between simulation and materialization is the core performance rule of the feature.
