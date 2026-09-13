# The Emerald Wire and variable growth

## Portable newspaper appearance

The same reusable Newspaper item rests as a rolled grey edition with a curled end,
printed columns and paper band. Opening that held copy unfolds it into the pocket
edition: layered sheets, folded corner, village illustration and dark printed columns.
The sprite switches immediately, rather than playing an unrolling animation. The
128px transparent textures preserve more detail in hand; the 16px inventory slot
necessarily shows a smaller impression. Both main and offhand are supported.

The source hand and exact server-side stack are scoped to the reader menu. Vanilla
synchronized item-use state selects the open model, so the model does not require a
second item, inventory exchange or persistent tag. Closing the menu, replacing or
removing the source ends the state. Other copies and the Desk's searchable browser
remain rolled. A display-only open-paper stamp appears in the portable reader when
there is room; it never enters a player inventory. Newspaper recipes are unchanged.

## Player actions become local reports

The network observes successful Survival player interactions with evidenced village property,
rather than assuming everything near a bell belongs to the village.

- Village food containers: net removal or addition of at least eight edible items over ten
  seconds. Taking and returning the same quantity within that window cancels out. Actual
  container clicks include shift-clicks; unrelated automation does not name a player.
- Village crop removal: a two-minute game-tick observation window allows replanting.
  Replanting is credited to the player actually observed placing the crop. An unobserved
  replant cancels an accusation without inventing an identity. Unloaded sites wait for inspection.
- Buildings: four or more observed structure-block removals in a developing incident.
  News records removal, not whether the player had permission to renovate.
- Existing confirmed player-caused villager deaths produce respectful local reports.
- Fund gifts of at least 64 E, replanting and food restocking can receive positive coverage.

Examples include “Pantry ledger discovers subtraction,” “Building report: several blocks have
left the meeting,” and “Village fund receives help; residents welcome practical optimism.”
Each local story states the observed action and quantity. Names are optionally anonymous;
exact district coordinates are withheld by default.
Satirical headlines do not establish intent, ownership agreements or guilt.

Related reports merge by stable player UUID, village and category into daily bulletins. Updates
retain the same article ID.
No news-created fine, guard hostility, market shock, debt or inventory mutation occurs.
The existing village simulation can still respond to actual harm.

Player-placed property and no-build exclusions are respected. Unknown ownership is skipped;
old modifications without recorded ownership can be ambiguous. Native structure metadata
is read only from loaded chunks. Creative/Spectator block and container actions are ignored.
Tracking holds at most 2,048 pending observations and 65,536 player-property exclusions per
dimension; property reporting fails closed if exclusion capacity fills. At most 256 pending
entries are considered each second, round-robin, so unloaded sites cannot starve ready reports.
For very large project histories, direct project ownership checks inspect at most 256 projects
per action; missing proof may mean no report. Observation records persist with native SavedData.
As with other world state, the last moments before a machine crash may not be saved.

## Regular market news

Five outlets share one server-authored archive with distinct coverage and commentary:
The Emerald Ledger covers financial analysis, The Redstone Wire technology, The Nether Post
trade routes, and The Overworld Observer communities. The Daily Gravel labels opinion/satire.
These editorial voices interpret the same facts without creating new price effects.

Seventeen event families cover automation, harvests, discoveries, supply crises, portal
reopening, rail disruption, copper infrastructure, coal surplus, enchanting festivals,
luxury-demand slumps, fishery recovery, potion recalls, credit scares, stress tests and
regional disaster/rebuilding. Authored headlines mix satire and grounded reporting;
tragedies focus on the incident and recovery, not mocking victims.

The current library has 96 event headlines, 24 directional roundup headlines and 15
outlet commentary passages, plus local-action wording. World-seeded choices, outlets,
measured price changes and follow-ups add variation. Recent wording is avoided for 90
economic days where the finite pool allows, then least-recently-used wording returns.
This is a finite authored system, not external AI or a promise of infinitely unique stories.

Event chances are 1.8% on an ordinary economic day, 2.5% in bull/recession/recovery
conditions and 3.5% in a boom or crash,
with a 30-day per-family cooldown. A roundup appears every second economic day without
an event. Follow-ups at two and seven days measure prices relative to the pre-event baseline,
without claiming that price recovery proves a crisis is over. Local follow-ups report changes
in the simulated food estimate; observed replanting/restocking can continue an earlier story
without claiming that every loss was repaired. Event dispatches explicitly describe off-screen trade conditions,
not invented destruction in a player's village. Disabling market events stops new shocks;
ordinary price reporting and player news remain available.

A market event and its price effect are resolved once by the economic simulation. Articles
describe the same event and include measured VILX/strongest/weakest daily changes. Reading,
crafting copies, player activity reports, archive eviction and reloads do not trigger shocks.
Event cooldowns are persisted separately from the news archive to prevent manipulation.

## Newspaper item and reader

Craft one Paper + one Ink Sac, shapeless, to make a reusable Village Newspaper.
Paper unlocks its recipe. The item is also in The Emerald Standard creative tab.
Use it anywhere, or choose News → Read The Emerald Wire at an Exchange Desk.

The portable item opens a paper-colored newspaper with a masthead, lead story, bylines and two-column
articles. The masthead is simply The Emerald Wire; its former fixed Overworld slogan is removed.
Faint stable fibres and fold lines sit behind the ink, with a small corner crease and margin tear.
Clickable Contents uses story numbers rather than pretending every article is one printed page.

The portable reading path is cover -> all Contents sheets -> every story in editorial order.
Wheel scrolling continues into the next story at the end of long text; Next first advances a full
two-column view, then changes story. Previous/upward scrolling reverses the route and enters the
previous story at its end. Only the cover/edition-end stops navigation, with disabled boundary buttons.
Front and Contents remain direct shortcuts. The footer identifies the story and visible text range.

Significance scores prioritize major events over routine roundups, with an eight-point-per-day
age penalty and deterministic date/ID ties so old disasters cannot occupy the cover forever. Scores
use typed event categories and bounded quantities, not untrusted wording or inferred guilt.

The Exchange Desk instead opens the existing channel browser. Section filters offer All, Markets,
Local, Players and Community; search and outlet filtering cover all 256 retained reports. These are
two views of the same privacy-filtered archive, not two independently generated news feeds.

Incoming stories wait behind New stories! rather than replacing the current reading snapshot.
Accepting retains the selected article ID if it still exists. Unread stars last for this reader
visit, not across sessions. Privacy changes override frozen editions as soon as synchronized.
Four unreachable read-only documents carry one coherent archive version; partial packet updates
are not mixed into an edition. Changed archives are checked approximately every five seconds. There is no
subscription, item consumption, remote trading or remote banking access.

The item uses a flat, vanilla-paper-style silhouette with grey shading and black printed marks.
Its dedicated 32-pixel transparent texture uses Minecraft's generated item model, including the
standard held/dropped-item transforms. Resource packs can replace the item/model normally.

## Privacy, community designation and editorial capacity

World settings expose four news switches: public player reports (default On), anonymous
players (Off), approximate locations (On: coordinates withheld), and explicit property only
(Off). Names/coordinates are filtered on the server before synchronization. Disabling public
reports also stops new local observations and hides retained local articles. The private server
archive remains; restrictions cannot erase information somebody previously saw or recorded.

Operators can designate a farm, container or building volume for reporting:

```text
/emerald news property add <from x y z> <to x y z>
/emerald news property remove <from x y z> <to x y z>
```

These commands change reporting metadata only, not blocks, development protection or financial
ownership. Up to 4096 positions per command and 16384 per dimension are allowed, with a nearby
managed village still required. Explicit designations override automatic player-property
exclusions for reporting. Explicit-only mode skips automatic structure inference.

The 256-article archive reserves 112 market, 32 local follow-up, 64 player-action and 48
community slots. Categories borrow unused space; eviction comes from the most over-reserve
category. Up to 64 developing-story records exist separately, with market stories protected
from local-story floods. Each story retires after its seven-day follow-up.

## Data-pack wording

Default wording lives at data/the_emerald_standard/emerald_news/templates.json. A pack can
replace that file, or add a JSON object under data/<namespace>/emerald_news/*.json. Each key
is an event enum name, ROUNDUP_UP, ROUNDUP_DOWN, PLAYER_<news kind>, or VOICE_0 through
VOICE_4 in the displayed outlet order. Values are arrays of plain single-line strings.
Use /reload to apply future wording; published history is not regenerated.

Overrides are processed in resource-ID order after Minecraft resolves pack priority for the
same path. Validation limits: 64 files, 64 groups, 32 alternatives per group, 300 characters
per alternative, and 256 Ki characters in total. Unknown keys and control characters are
rejected. A failed reload retains the previous valid catalog and logs the reason.
Templates affect wording only, never market price effects, evidence or privacy policy.

## Investment philosophy

Stocks, commodities and the shared broad economic factor use smoothly changing positive
annual fundamentals targets, not a floor on realized investment performance:

| Sampled target | Base probability |
| --- | ---: |
| 1–4% | 70% |
| 4–8% | 23% |
| 8–12% | 6% |
| 12–15% | 1% |

Successive seeded targets blend over 181 economic days. The weights describe sampled
endpoints, not the precise time spent in each band. Coal/diamond favor the low end;
copper/redstone and selected growth companies tilt somewhat higher.
One economic year is 365 economic days. Actual returns can be negative for a year or
even a long holding period, and gains can exceed 15%. There is no year-end catch-up,
guaranteed recovery or reload reroll.

Companies keep their sector differences. VILX follows their twelve-company capitalization
basket without an independent return draw, extra news shock or upside limiter. Its indicative
fundamental bias is the current weighted mean of company targets, not promised performance.
TREA remains a
rate-sensitive treasury fund; savings/CD rules are unchanged.

Commodity reference values grow gradually while spot prices respond to supply/demand,
shared industrial shocks, regimes and occasional turbulence. Annual noise settings:
Gold 18%, Iron 32%, Coal 45%, Diamond 22%, Copper 28%, Redstone 35%, Lapis 25%,
Netherite Scrap 40%. These are gameplay assumptions, not empirically fitted forecasts.
Physical Trade and commodity investments retain the same underlying quote.

## Compatibility and handbook

Format 33 preserves current holdings, cash, cost bases, quotes and recorded history. Format-31
articles receive stable IDs without rewriting their text; unknown historical event baselines
are not invented. Older pre-news saves still begin with an empty archive.
Older saves initialize new commodity reference values at their current quotes and begin
with an empty newspaper archive; no synthetic history is inserted. Existing price history
is not backfilled; denomination splits adjust price histories and follow-up baselines consistently. Back up the whole world, use matching client/server builds, and do not
downgrade a migrated save.

The handbook now has 67 long-form sections, 60 compact/lectern pages and four animated
crafting recipes. The new material explains targets versus returns, commodity volatility,
reporting evidence, limitations, reader controls and crafting.
