# Beta 68: Bank smoke marker and icon-matching Banker

## Scope

Every new Bank style/design receives a real lit campfire hidden in a masonry chimney, with concealed hay beneath and an uncapped smoke flue. The 3×3 roof-bearing base never expands the X/Z lot envelope or occupies a declared access route. The only existing roof edits are snow immediately under the base and bottom slabs completed to double slabs to eliminate half-block floating gaps. The hidden campfire and hay remain core geometry in every outdoor fit. A high rim and full sides screen the flame/hay from street level. Materials follow plains/savanna brick, desert cut sandstone, taiga cobblestone and snowy stone brick.

Official Minecraft behavior was checked against [Mojang's campfire introduction](https://www.minecraft.net/en-us/article/minecraft-snapshot-19w02a) and [campfire signal-smoke guidance](https://www.minecraft.net/da-dk/article/taking-inventory--campfire). Native 26.2 CampfireBlock properties and model/renderer code were inspected locally. The smoke emitter uses LIT=true, SIGNAL_FIRE=true and WATERLOGGED=false with a real CampfireBlockEntity; no artificial particle effect is added. The native fire does not spread, though direct campfire contact can hurt entities, so the roof flue is not a walking route. Water extinguishing remains normal gameplay.

The existing deterministic pixel-art generator supplies the icon-matching charcoal suit, white collar/cuffs, emerald tie and black sunglasses. Native profession hat pixels carry only glasses; FULL profession metadata suppresses biome hats, leaving native bald facial skin/nose untouched. Both arm maps, underlying suit/trousers and distinct zombie UVs/palette are covered. Native renderer inspection caught biome clothing leaking through transparent folded forearms; these now carry opaque suit fabric/cuffs, while zombie hands remain native. No renderer, profession, AI, access or spawn behavior changes.

## Compatibility

Revision 15 and three nested fit catalogs have their own native SHA-256 identities and measured heights. Bank full versions 22–24 and fits 128–130, 144–146, 160–162 decode separately from all historical versions. All v12/v13/v13-fit/v14/v14-fit resources stay unchanged; 1,440 non-Bank full/fit assets in the new release match revision 14 exactly. Already completed/saved Bank geometry is not altered. Existing Banker villagers receive updated profession resources on reload. Save format remains 45; vanilla-exact assets, live mod profiles and player worlds are untouched.

## Handbook review

Both the compact Bank styles page and the long Bank access chapter explain smoke, clothes, fallback retention, reload behavior, water extinguishing and unchanged old buildings. HandbookChapters routing and EmeraldHandbook's 70-page layout remain accurate. HandbookRecipes, HandbookScreen and server definitions were reviewed: no new item/block/recipe is registered, and campfire/hay remain vanilla content, so unrelated crafting animations are unchanged. Regression assertions and native client page fitting cover the new prose.

## Validation

Authoring admission caught snow-covered bearings and floating masonry above bottom slabs; both were fixed within the chimney footprint without weakening native contact support or façade scope checks. Repeated authoring now checks actual added cells before treating an input as already finished; an additions-cache entry alone cannot suppress a chimney on a fresh input. The asset gate covers 60 current Bank style/design/fit combinations, repeated/idempotent authoring, full sides, open flues, native lit/signal states, retained access/capacity, stage placement and historical version decoding. The production server fixture tests construction, saved restart, operational desks, walkways, native connections and surviving smoke emitters across all 60 combinations.

The expanded walking fixture uses each Bank's declared street exit rather than inferring outdoors from a door's FACING property. It visits the nearest accessible side of the workstation and uses a non-job-seeking visitor, so native profession acquisition cannot redirect a customer into the staffed teller alcove during a prescribed return-to-street test. These are opt-in fixture corrections, not changes to production navigation or saved geometry. All walking checks still require physical travel, native door opening and an actual return; no teleport or skipped collision check is used.

The opt-in native Banker client check covers all seven villager types from three angles, profession metadata, the updated compact Bank styles page and all 16 long-form reader chapters with native font wrapping and scroll-end checks. The ordinary full compact-handbook client gate exposed pre-existing overflow in unrelated legacy compact pages; it is not reported as passed. Only the changed compact Bank page is admitted in this targeted run, while the normal all-page gate remains strict and unchanged.

Passed verification:

- Fabric full build (`build/beta68-fabric-build.log`), followed after fixture/cache corrections by final `assemble verifyApprovedArchitecture` (`build/beta68-fabric-final-package.log`). The final native gate includes all 375 current designs, every archived full catalog, 3,375 fitted assets, repeated Bank authoring, exact current frozen-versus-preview geometry, roof/headroom/support/lighting and historical version proofs.
- NeoForge final full build (`build/beta68-neoforge-final-package.log`), including its actual patched-loader unit-test bootstrap and all registered verification tasks.
- Complete common regression suite (`build/beta68-common-tests-final.log`), including exact human/zombie Banker UVs, hat metadata and handbook assertions.
- Both dedicated-server harnesses completed every one of 103 fixtures, with zero remaining and the final integration success marker (`build/beta68-fabric-server-final-harness.log`, `build/beta68-neoforge-server-final-harness.log`). This includes all 60 current Bank design/fit combinations plus historical structures and completion/recovery fixtures. The harness terminates only its own uniquely marked disposable server processes after success; trailing Gradle daemon-termination messages are cleanup, not failed fixtures.
- Fabric native client preview succeeded (`build/beta68-banker-client-final.log`); front/back/side screenshots were inspected. The delivered `build/release-beta68/banker-preview.png` is the actual rendered front view, not a mockup.
- Both staged binary/source pairs passed `verify-built-jar.sh`; both binaries passed current-source and 4,875 native-asset SHA-256 checks. Both contain PNGs and metadata byte-identical to the final source resources. All 3,380 historical asset/manifest files are unchanged from beta67, and all 1,440 current non-Bank full/fit assets match revision 14.

Canonical source SHA-256: `11496c7b5d9aeb6485f42ebe0f6cfc3249a3aa24f8f5c601d4445184fce30ff2`.

Staged release: `build/release-beta68`, with loader-specific binary/source pairs and SHA256SUMS.

- Fabric binary: `0e86a3f10013b22d2b7b0d0ed2bcba1b3ea57ca6fa952e077574e275a063c093`.
- Fabric sources: `a6fb9e8b64558198e44360ebdf51f14575613e7e28a2f79dc189256fc3e0afd3`.
- NeoForge binary: `5de52c74f8e723c0d01ee43beba15957bdf9ded91fd07e1844dc30a8b4d9edc2`.
- NeoForge sources: `16f9f3d5160a887d07e76c8b61acc85546b68596270197df8aa7d33a36b21327`.

No automated retrofit, live-profile installation or modpack performance claim is made. Test new construction in a world copy; keep the old JAR and backup.
