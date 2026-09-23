# KL Travel v1

Android app (Kotlin + Jetpack Compose). Reads your Google Sheet, shows a day-by-day timeline,
routes to each event (drive / rideshare / transit / walk) on Google Maps, keeps hotel details,
and sends flight and leave-by alerts.

## Setup (do these in order)

1. **Open the project.** Android Studio > Open > this folder. Let Gradle sync.
2. **Google Maps key.**
   1. console.cloud.google.com > create a project and enable billing.
   2. APIs & Services > Library > enable **Maps SDK for Android** and **Routes API**.
   3. Credentials > Create credentials > API key.
   4. Restrict it: Application restrictions = Android apps, package `com.kl.travel` plus your SHA-1
      (Android Studio: Gradle > app > Tasks > android > signingReport). API restrictions = the two APIs above.
   5. Paste it into `local.properties`: `MAPS_API_KEY=AIza...`
3. **Flight API key** (pick one, entered later inside the app).
   - AeroDataBox on RapidAPI: subscribe to the free Basic plan, copy your `X-RapidAPI-Key`.
   - aviationstack: create a free account, copy the access key.
4. **Sheet.** Use `KLTravel_Template.xlsx` (or your loaded copy): upload to Google Drive, Save as Google Sheets,
   Share > Anyone with the link > Viewer. Keep tabs named `Events` and `Hotels`.
5. **Run.** Plug in your phone (USB debugging on) and press Run. Or Build > Build APK(s) and sideload it.
6. **In the app.** Settings > paste the sheet link > Save & sync. Then Settings > Flight updates: pick the provider, tap "Get my free ... key" (it opens the sign-up page and shows the steps), and paste the key. Swipe left/right to move between the bottom tabs.
   Allow notifications (and location if you want routes to start from where you are).

## Sheet columns

Events: Date, Start Time, End Time, Type, Title, Location, Address, Flight #, Confirmation #, Cost, Transport, Notes
Hotels: Hotel Name, Address, Check-In Date, Check-In Time, Check-Out Date, Check-Out Time, Confirmation #, Phone, Cost, Notes

## How the alerts work

- Runs about every 15 minutes in the background and re-reads your sheet.
- Flights: checked within 24 hours of departure, more often as departure gets closer. A monthly lookup cap protects free API quotas.
- Leave-by: for events in the next 4 hours, gets live traffic from the Routes API and notifies at your lead time, again at "leave now",
  and if the trip time changes by 10+ minutes.
- Background alerts start from your previous stop or hotel (Android limits background location). Open the event to route from your live location.

## Tools tab, weather, widget and documents

- **Currency converter** (Tools tab): rates from Frankfurter, backed up by ExchangeRate-API's open endpoint. No key needed. The last rate is saved, so it still works offline (it says so).
- **Weather**: shown under each day on the timeline and listed in Tools. Open-Meteo forecast for the next 16 days; farther out it shows the same dates last year, labelled "typical". Place = the most common Location that day in your sheet.
- **Home-screen widget "KL Travel"**: long-press the home screen > Widgets > KL Travel. Shows the next event, leave-by time and flight status. Refreshes with the background check (about every 15 minutes) and on every sync. Works offline from the saved trip.
- **Documents** (Tools > Open documents): add passport, visa, ticket or insurance PDFs and photos. They need your phone's screen lock to open, block screenshots while open, stay on the phone (no backup, no Sheet, no server), and re-lock when the app goes to the background. Delete removes the file for good.

## Known limits

- Rideshare fares are not available through any public API. The app shows drive time and opens Grab / Uber / Lyft.
- Times use the phone's time zone. Fine for a trip in one country (the phone switches when you land).
- Transit and traffic predictions far in the future may be limited by Google; the app shows Google's error message if so.

## Sync note (hotel confirmation numbers)
Google's `gviz` CSV blanks cells that don't match a column's dominant type (e.g. `K2E7Y1` in a column of numeric confirmations). The app therefore reads each tab through the plain CSV export (`/export?format=csv&gid=…`), finding tab gids from the sheet page, and only falls back to `gviz` if that fails. The sheet must still be shared as "Anyone with the link: Viewer".

## Entering a Maps key inside the app
Settings > "Google Maps key" lets anyone paste their own key, so a second phone needs no rebuild. It overrides the build-time key and is used for the Routes API and for the in-app route map (Maps JavaScript API in a WebView, because the native Maps SDK only reads its key from the manifest at build time). Enable **Routes API** and **Maps JavaScript API** on that key, and turn billing on. Restrict the key by API only, not by Android app or referrer.

### How to get a Maps key (also shown in Settings > Google Maps key > "How to get a key")
1. Sign in at console.cloud.google.com, create a project (KL Travel).
2. Billing: link a billing account (free monthly usage applies).
3. APIs & Services > Library: enable Routes API and Maps JavaScript API (add Maps SDK for Android only if you build the key in).
4. Credentials > Create credentials > API key, copy it.
5. Restrict the key to those APIs (no application restriction).
6. Paste it in Settings and tap Save key.

## Hotel photos
The Hotels tab and hotel check-in/out screens show a photo of the hotel (Google Places API (New): text search on name + address, first photo). Enable **Places API (New)** on your Maps key and add it to the key's API restriction. Photos are downloaded once per hotel and cached on the phone, with the photographer credit shown as Google requires.

## Seats and plane type
Optional Events columns **Seat** and **Aircraft** (headers are matched case-insensitively; also "Seats", "Plane", "Aircraft type"). The flight screen shows them (several travelers: write "Luis 3C / Sabrina 3D" and each seat is listed on its own line with the name); if Aircraft is blank it uses the plane type from the live flight lookup (needs the flight API key in Settings). The **Seat map on SeatMaps** button opens seatmaps.com directly: the exact plane's seat map when known, otherwise that airline's seatmaps.com page. The **Change seat on ...** button opens the airline's manage-booking page (EVA, Philippine Airlines, Cebu Pacific/Cebgo known; others fall back to a search) and copies the first booking code. Local database is now version 2 (a migration adds the two columns and keeps your expenses).

## Airport lounges
On a flight screen, **Airport lounges** lists lounges at the departure and arrival airports (chips switch airport). Tap "Show lounges" to load them from Google Places API (New), which also needs Maps JavaScript API for the map; results are cached 30 days per airport. Each lounge has a numbered map pin, address, a **Navigate** button (Google Maps walking directions to the lounge) and **Details**. Settings > **Lounge access** lets you tick memberships (Priority Pass, LoungeKey, DragonPass, Plaza Premium, Amex, Capital One, Chase, Diners, airline status or business ticket) and add notes; lounges you may get into are labelled, with an "only lounges I may get into" filter. Matching is a best guess from lounge names, so confirm access rules, guest limits and hours. Lounges that Google Maps doesn't list won't appear.

## Sheet template and "Lodging"
Hotels are now called **Lodging** everywhere (tab, screens, labels). The app reads a tab named Lodging, and still accepts an older tab named Hotels and the header "Hotel Name". Settings > Google Sheet > **Get a new sheet template** opens the bundled KLTravel_Template.xlsx in Google Sheets (then File > Save as Google Sheets, share as Anyone with the link: Viewer, paste the link). The template has Events (with Seat and Aircraft), Lodging, and a How to use tab; confirmation, seat and phone columns are formatted as text.

## Layovers
A card between two connecting flights shows "Layover in TPE: 2h 15m" (Timeline and Transport tabs, and on each flight's screen). It appears when the first flight lands where the next one leaves, less than 24 hours apart. For it to work, put the airport codes in each flight Title ("EVA BR49 - DFW > TPE") and fill End Time on the first flight with its arrival time (local time at the arrival airport). If End Time is blank the app also reads the Notes ("arrives Taipei 5:25 AM on Feb 23"); put the date in when the flight lands on a different day than it leaves. A stay of 12+ hours or with a hotel check-in between shows as a Stopover. Under 60 minutes shows red, over 6 hours is flagged as a long layover, and with a flight API key a late flight moves the layover time (and warns if the connection looks missed). Tap the card to open the next flight and its lounges.

## Sheet colors and day gaps
Template 2.6 colors each Events row by its Type with conditional-format rules (Flight blue, Boat/Ferry teal, ground transport yellow, Lodging purple, Meal orange, Wedding pink, Activity/Tour/Event green) and Lodging rows purple. Leave two empty rows between days; the app ignores empty rows. The colors are only in the sheet, the app does not read them.

## Column widths
Template 2.7 sizes columns to fit their headers and wraps long text. The hosted Google template also carries a small Apps Script (`onOpen` and `onEdit`, simple triggers) that calls `autoResizeColumns` on Events and Lodging, capped at 380 px. It lives only in the Google copy; it is not in the xlsx and the app does not use it.

## Google usage limits
Every call the app makes with the Google Maps key goes through `data/GoogleUsage.kt`: `tryUse`/`require` count it per month and refuse past the cap set in Settings > Google usage (routes in `RoutesClient`, Places in `LoungeClient` and `HotelPhotoClient`, map loads via `rememberMapLoadAllowed` in `WebRouteMap.kt`). `RouteCache` (in `RoutesClient.kt`) keeps routes for 10 minutes; `RouteThrottle` slows the background leave-by check for far-off events. The native (built-in key) map is free and not counted. Google's own daily quotas remain the hard stop (see Settings > Keep Google free).

## Transport pictures
Flight, ferry, train, bus and tricycle screens show a photo of what you ride, found on Wikimedia Commons (free, no key) and saved on the phone. A flight searches airline plus plane (Aircraft column, else the live flight lookup), then the plane type, then the airline. Ferries use the operator in the title or Transport column (OceanJet, 2GO...). Rows where you drive show your own vehicle from Settings > My vehicle (type and/or photo). Every row also has "Add my own photo" and "Reset". Code: net/TransportImages.kt (what to search and which file to pick), net/TransportPhotoClient.kt (download, cache, your own photos), ui/TransportPhoto.kt.

## Business lounges per flight
Events has an optional **Cabin** column (Economy, Premium Economy, Business, First; also "Class"). It is shown as information only. The old Business / first class ticket option on the lounge card was removed in 1.11.2 (domestic first class does not include lounge access); lounges match only the programs you tick in Settings.

## Versions
The APK we hand out is built with `./gradlew assembleSmall` (R8-shrunk, about 9 MB, signed with the debug key so it updates over older installs).
App version lives in `app/build.gradle.kts` (`appVersion`), template version in `data/Versions.kt`; both are listed in CHANGELOG.md and a unit test fails if they disagree. Output files are named with the version, e.g. KLTravel_v1.9.0.apk, KLTravel_v1.9.0_project.zip, KLTravel_Template_v2.2.xlsx. The launch splash plays the whole KL crown video (app/src/main/res/raw/kl_splash.mp4) with the version number under it. Tap anywhere to skip it. The app icon (`app/src/main/res/drawable-nodpi/ic_launcher_fg.png`, referenced by `mipmap-anydpi-v26/ic_launcher.xml`) is the crown-and-KL mark with "TRAVEL" underneath, so it reads apart from the other KL-branded apps on the home screen.

## Multiple trips
Each trip = a name + a Google Sheet link, stored on the phone; events, lodging and expenses are saved per trip. Open the picker from the trip name at the top of the timeline or Settings > Switch or add a trip. On first launch after updating, your existing sheet becomes "My trip". The document vault, currency and lounge settings are shared across trips. Alerts and the widget use the trip that is open.

## API blocked error
If lounges, photos or routes show "Requests to this API ... are blocked", the key is not allowed to use that API. Settings > Google Maps key > **Fix: API is blocked error** lists the steps: enable Places API (New), tick it (plus Routes API and Maps JavaScript API) under the key's API restrictions, check billing, wait 2 to 5 minutes.

## Timeline, Transport and Activities: day grouping
All three tabs share the same day-grouped list (`ui/buildRows` in `TimelineScreen.kt`). Days are separated by a divider line, not just spacing. A day that has already ended starts collapsed into a "N items" row under its date; tap the date to open it back up (it stays open until you tap it shut again) or to close it. Today and future days always show in full. `buildRows` keeps a collapsed day's Header row but drops its Entry/Connection rows; each screen tracks which past days the user reopened separately (Timeline, Transport and Activities each remember their own).

## Transportation and Activities tabs
The bottom bar is Timeline, Transport, Activities, Lodging, More (Expenses, Tools and Settings are under More). **Transport** lists every event whose Type is Flight, Transport, Boat, Ferry, Train, Bus, Drive, Taxi, Rideshare, Shuttle, Transfer, Van, Car and similar. **Activities** lists the other events except lodging (Activity, Meal, Tour, Wedding, Meeting, Event...). Each tab groups by day and opens the same detail screen as the timeline. Lodging is a flat list of stay cards (not day-grouped) and already marks a stay "Past stay" on the card itself.

The Google Maps key steps in Settings have tappable buttons that open the matching Google Cloud page (project, billing, Routes API, Maps JavaScript API, Places API (New), your keys).

## Lounge list default
The lounge card shows only the lounges your Settings > Lounge access memberships match. Tap **All lounges** to see every lounge on Google Maps; a warning says access may not be given at the ones that do not say "You may get in with". With no memberships ticked it shows everything (with the same warning).

## Backup and restore
Settings > Backup and restore. **Back up** writes KLTravel_backup_DATE.json (Maps and flight keys, lounge memberships, alert settings, trips, saved travel modes, expenses) and opens the share sheet so you can save it to Drive, Files or email it to yourself. **Restore** picks that file and puts everything back, then syncs. Caches (weather, lounges, flight status) are not saved. The file holds your API keys: keep it private. Updating the APK over the top never needs this; it is for a reinstall or a new phone.

## Receipts
Expenses > camera icon > Take photo (or From gallery). Text is read on the phone with Google ML Kit (bundled model, works offline; the photo is never uploaded). The app fills merchant, date, total, currency and a category guess; foreign totals are converted to USD with the currency tool's rate (editable). Save keeps the photo inside the app; tap a receipt row to see it, delete the expense to delete the photo. Backups keep the amounts but not the photos. Expenses show spending by category. Database version 5.


## First-run setup
A fresh install opens a step-by-step guide (sheet link, Maps key, flight key, lounges, alerts and permissions). Skip setup (top right) or Skip this step is always available; everything is also in Settings, and Settings > About > Run the setup guide again reopens it. Installs that already have a trip or key skip it automatically.


## New trip template
Settings > Google Sheet > "New trip: get a blank sheet" opens https://docs.google.com/spreadsheets/d/1MJ4WKVJwA5wH8YEtU49SzjKEl8cdId8kQz94jEKgKLo/copy (TEMPLATE_COPY_URL in TemplateShare.kt), a view-only Google Sheet copy of template 2.6 with no data. Update that hosted sheet whenever Versions.TEMPLATE changes.
