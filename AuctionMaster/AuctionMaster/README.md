# AuctionMaster

A Paper/Spigot auction house plugin, built in the spirit of ZelAuction with the features
that plugin doesn't have.

## What's better than ZelAuction

- **Live bidding auctions**, not just fixed Buy Now listings. A listing can have a
  Buy Now price, a bidding starting price, or both. Includes anti-snipe (a bid in the
  last N seconds extends the auction so it can't be sniped at the last second).
- **Cross-server shared auction house.** Switch `storage.type` to `mysql` in
  `config.yml` and every server pointed at the same database shares one auction house -
  useful for networks (BungeeCord/Velocity) with multiple survival/skyblock/prison servers.
- **Discord webhook alerts** for big sales, configurable by minimum price threshold.
- **PlaceholderAPI support** - `%auctionmaster_active_listings%`,
  `%auctionmaster_my_listings%`, `%auctionmaster_pending_balance%`.
- **Collection box + pending balance system** - if a payout can't be delivered
  immediately (player offline, inventory full), it's queued and safely collected later
  via `/ah collect` instead of being lost.
- Sale tax, configurable per-listing limits, configurable auction duration - all standard,
  all included.

## Project layout

```
src/main/kotlin/clouddev1stack/
  AuctionMaster.kt          - main plugin class, wiring
  model/                    - Listing, HistoryEntry, ListingStatus
  storage/                  - Storage interface + SQLite/MySQL (HikariCP) implementations
  economy/                  - Vault economy wrapper
  manager/                  - AuctionManager: all business logic (buy/bid/cancel/expire)
  gui/                      - Inventory GUIs (browse, collection box)
  listeners/                - Inventory click + chat-based bid input
  commands/                 - /ah and /ahadmin
  placeholder/              - PlaceholderAPI expansion
  discord/                  - Webhook notifier
```

## Building

Requires JDK 21 and Maven, plus internet access to Maven Central + PaperMC's repo (the
sandbox this was written in could not reach those, so this hasn't been compiled yet -
build it locally):

```bash
mvn clean package
```

The output jar will be in `target/AuctionMaster-1.0.0.jar`. Drop it in your test
server's `plugins/` folder.

## Before you sell this on BuiltByBit

1. **Test it.** This is a full, real implementation, but it hasn't been compiled or run
   against a live server yet. Spin up a local Paper 1.21 test server, install Vault +
   an economy plugin (e.g. EssentialsX), and walk through: sell, buy now, bid, outbid
   refund, cancel, expiration sweep, collection box.
2. **Licensing:** wire up BuiltByBit's official Enigma licensing API rather than a
   custom validation server - it's what buyers and BBB staff already trust, and you
   won't be stuck maintaining a validation endpoint yourself.
3. **Resource page:** BuiltByBit listings sell on the demo video/GIF as much as the
   feature list - a 30-60s clip of the bidding + anti-snipe extension in action will
   do a lot of work for you.
4. Consider a `/ah search <name>` filter and per-category tabs (weapons/armor/blocks/etc)
   as a fast v1.1 - those are common review requests on similar plugins.
