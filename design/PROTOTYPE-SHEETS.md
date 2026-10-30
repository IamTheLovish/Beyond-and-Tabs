# Prototype design sheets (read before trusting a number)

Status note, Oct 26 2026.

`design/economy.json` and `design/ai.json` (and the other `design/*.json` sheets that `tools/gen_java.py` reads) belong
to the old from-scratch prototype in `engine/` and `mod/`. `tools/gen_java.py` turns them into
`engine/src/main/java/dev/beyondtabs/engine/gen/*.java`, and CI diff-checks that output, so the JSON must stay valid
and unchanged unless the prototype is meant to change. That is why this note is a separate file and not a `_status`
key inside the JSON.

They hold **prototype values** (for example a 1000/1000 start and "T2 at 15 metal/s"). They are not the live game.
The live numbers are in the Forge mod under
`bar/src/main/java/com/solegendary/reignofnether/resources/` (`Resources.java`, `TierGate.java`, `EconomyMath.java`,
`EconomyPace.java`) and the unit/building classes. Pending balance changes are PRs, for example #221 (slower tier 2).

Do not copy a number from these sheets into the live game or into a design doc without checking the Java first.
