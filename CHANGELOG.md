# KL Travel changelog

Every change gets a new number. App: major.minor.patch (new feature = minor, fix = patch). Template: major.minor (new or renamed columns/tabs = major, small additions = minor). Files are named with the version, e.g. KLTravel_v1.9.0.apk and KLTravel_Template_v2.4.xlsx.

## App 1.25.1 - 2026-09-22
- App icon: added "TRAVEL" under the crown-and-KL mark, in the same blue with a cyan glow, so it's easy to tell apart from your other KL apps at a glance on the home screen. `ui/` assets only, no code change. Asset: `app/src/main/res/drawable-nodpi/ic_launcher_fg.png`.

## App 1.25.0 - 2026-09-22
- Transport and Activities tabs now match the Timeline: a divider line between days, and a past day starts collapsed into a "N items" dropdown you tap open (or shut again).

## App 1.24.0 - 2026-09-22
- Timeline: a clearer break between one day and the next (a divider line, not just extra padding).
- Timeline: a past day now starts collapsed into a "N items" dropdown instead of sitting in the list dimmed out. Tap the day's header to open it back up (it stays open until you tap it shut again) or to close it. Today and future days are unaffected and always open.
- Tests: 1 new (buildRows keeps a collapsed day's header but drops its entries and layover, including when every day is collapsed), 119 in all.

## App 1.23.2 - 2026-09-21
- Bundled sheet template is now 2.7 (see Template 2.7 below). No change to how the app reads a sheet.

## App 1.23.1 - 2026-09-21
- Bundled sheet template is now 2.6 (see Template 2.6 below). No change to how the app reads a sheet: empty rows between days are already ignored.

## App 1.23.0 - 2026-09-21
- Google usage limits, inside the app. Settings > Google usage counts this app's Google calls each month (Routes, Places, map loads) and stops at a limit you set. Defaults sit well under Google's free allowance: Routes 3,000, Places 1,000, map loads 3,000. At the limit the feature says so (map hidden, "limit reached, raise it in Settings") instead of calling Google. Limits are included in Backup and restore; the month's count is not.
- Fewer repeat calls: a route is reused for 10 minutes (reopening a screen, flipping between Drive and Rideshare, or standing still no longer calls Google again). The background leave-by check waits 30 to 60 minutes for events more than 90 minutes away instead of every 15, about a third fewer background calls over the 4 hours before an event.
- The route map is loaded only once a route exists, not once empty and again with the line, so each screen open is one map load instead of two.
- Live traffic switch (Settings > Google usage). Off uses plain travel time, Google's cheaper Routes tier with a 10,000 a month free allowance instead of 5,000. On by default; nothing changes unless you turn it off.
- Google's console caps (Settings > Google Maps key > Keep Google free) are still the only hard stop at Google's side; this new counter only sees this app's calls.
- Tests: 15 new (counting and limits, month rollover, no network call at the limit for routes, lounges and lodging photos, route reuse, background throttle, map-load counting on screen, backup of the limits), 118 in all.

## App 1.22.1 - 2026-09-21
- Much smaller download for the phone: 26.5 MB down to 9.2 MB. The APK you install is now a shrunk build (unused code and icons are stripped) instead of the debug build. Same app, same signature, so it installs over the top of 1.22.0 and keeps your data.
- Build note: hand-out APK is `./gradlew assembleSmall` (app/build/outputs/apk/small/app-small.apk); tests still run on the debug build.

## App 1.22.0 - 2026-09-21
- Transport pictures: a flight, ferry, train, bus or tricycle screen now shows a photo of what you will ride. EVA Air BR49 on a Boeing 787-9 shows an EVA Air 787-9; OceanJet shows an OceanJet ferry; Philippine Airlines on a Dash 8 shows that plane. The plane comes from the Aircraft column, or from the live flight lookup if the column is blank. If no photo of that exact plane exists it falls back to the plane type, then the airline.
- Photos are free ones from Wikimedia Commons (no key, no cost), saved on the phone after the first load so they work offline. Interior, seat, logo and map pictures are skipped. The photo is labelled "Example photo, your actual vehicle may differ" with the photographer and licence under it.
- Driving: Settings > My vehicle. Type what you drive (for example 2019 Toyota RAV4), add a photo, or both. Rows where you drive or use a rental car show it. A photo wins over the typed name; with neither, the row says where to add it. The vehicle name is included in Backup and restore (the photo stays on the phone).
- Any transport row: "Add my own photo" (or "Use a different photo") overrides the picture for that row, and "Reset" brings the example photo back. Taxis, Grab and vans show no picture, only the add-your-own button.
- Tests: 24 new (what each kind of row searches for, picking the right file from a real-format Commons response, skipping interiors/PDFs/small files, cache names, backup of the vehicle, and screen-level checks of the photo card), 103 in all.

## App 1.21.1 - 2026-09-21
- Layover fixes, checked against the real Philippines 2027 sheet:
  - The arrival can now come from the Notes ("arrives Taipei 5:25 AM on Feb 23", "arrive 3:20 PM") when End Time is blank. That was the missing Feb 23 layover at Taipei (BR49 to BR184, 2h 30m).
  - Flights days apart at the same airport are no longer treated as a connection (BR184 landing in Tokyo and PR431 leaving Tokyo two days later was showing a false 21h layover).
  - Gaps of 12 hours or more, or with a hotel check-in in between, show as "Stopover in TPE: 22h 25m" (Mar 20 to 21) instead of a red long-layover warning.
  - If two flights meet at an airport and the first has no arrival time anywhere, the card now says "arrival time missing" and how to add it, instead of showing nothing.
- Tests: the real sheet's flights and Caesar Park stay are now a test, plus the new cases (79 tests total).

## App 1.21.0 - 2026-09-21
- Layovers: when one flight lands at the airport the next flight leaves from (within 24 hours), a card sits between them on the Timeline and Transport tabs: "Layover in TPE: 2h 15m", with the landing time and the next departure. Red for a short connection (under 60 minutes), amber-toned for a long one (over 6 hours), and "Connection at TPE at risk" if the times no longer add up. Tap the card to open the next flight, whose lounge list starts at that airport.
- Flight screens show the same card for the flight before and the flight after.
- Live times: if the flight-status lookup says a flight is late ("Arrives 15:45" or "Now departs ..."), the layover moves with it and says "live times".
- Needs the airport codes in the flight Title ("EVA BR49 - DFW > TPE", arrows and "to" also work) and an End Time (arrival, local to the airport) on the first flight. The Start Time of the next flight is its departure. If the codes or End Time are missing, no layover card is shown rather than a wrong one.
- Fix: the lounge lookup now understands titles written with ">" (like the template example "TPE > NRT"), not only arrows and dashes.
- Tests: 12 new (route reading, same-day, overnight, date-line, long, short, delayed, missed, row order) plus a screen-level check of the card.

## App 1.20.4 - 2026-09-21
- Smaller download: the APK now carries 64-bit (arm64) code only, about 3.7 MB smaller (30.1 MB to 26.4 MB), because the phone download from chat was stalling. Every phone made since about 2019 is 64-bit. It installs over the top of the old version like any update.

## App 1.20.3 - 2026-09-21
- Splash screen: tap anywhere to skip the logo video. It still plays to the end if you don't tap. A small "tap anywhere to skip" note sits next to the version number.
- Added an automated tap-to-skip test (Robolectric); it fails without the tap and passes with it.

## App 1.20.2 - 2026-09-21
- AeroDataBox key help rewritten from phone screenshots. The main step is now "Open My Apps": on the Manage Apps page tap the shield icon under Authorization on the default-application row to see the Application Key. The key page (tap the </> Code snippets icon on the right edge, or Chrome > Desktop site) is the alternative.

## App 1.20.1 - 2026-09-21
- Flight key help: "Open my key" for AeroDataBox now opens the Flight status page where the key shows under the Headers tab once you are signed in and subscribed (it used to open the API overview, which never shows a key). Added a "My apps" backup link and the real free-plan limits (AeroDataBox 400 units a month, about 200 lookups; aviationstack 100 lookups a month).

## App 1.20.0 - 2026-09-21
- Swipe left and right anywhere on a bottom-tab screen to move between Timeline, Transport, Activities, Lodging and More. Tapping a tab still works and now slides there.
- Flight updates key: a "Get my free AeroDataBox key" (or aviationstack) button in Settings and in the first-run guide opens the page where you sign up, plus numbered step cards with a button for each step (sign up, pick the free plan, copy the key). The button follows the provider chip you have selected.

## App 1.19.0 - 2026-09-21
- Settings > Google Sheet: "New trip: get a blank sheet" opens a blank copy of the template (no data) in Google Sheets. Tap Make a copy and it lands in your own Drive; then fill it in, share it as Anyone with the link: Viewer, and paste the link. "Use the file" still opens the bundled .xlsx. The first-run setup guide uses the same button.
- The blank template is hosted as a view-only Google Sheet (template 2.5). When the template version changes, the hosted sheet must be updated too.

## App 1.18.1 - 2026-09-21
- Bundled sheet template updated to 2.5 (see below).

## App 1.18.0 - 2026-09-21
- First-run setup guide: the first time the app opens (fresh install only) it walks through the sheet link (with the template button), the Google Maps key (with the numbered how-to cards and the keep-it-free caps), the optional flight-updates key, lounge memberships, and alerts, notification and location permissions, then shows what was done and what was skipped. "Skip setup" is at the top of every screen, each step also has "Skip this step", and there is an "I have a backup file to restore" button on the first screen.
- Settings > About > "Run the setup guide again" reopens it. Phones that already have a trip or key never see it automatically.
- Notification permission is now asked in the guide instead of the moment the app opens.

## App 1.17.1 - 2026-09-21
- Seats: when the Seat column holds more than one seat (for example "Luis 3C / Sabrina 3D"), the flight screen lists every seat on its own line with whose it is ("Seats (2)", "Luis: 3C", "Sabrina: 3D"). Works with "/", ";", "," "&" or "and" between seats, and with the name before or after the seat. Transport list shows "Seat Luis 3C, Sabrina 3D".

## App 1.17.0 - 2026-09-21
- Settings > Google Maps key: new "Keep Google free (usage caps)" card with numbered steps and buttons that open the exact Google Cloud quota pages. Explains that Google has no monthly cap, so you set daily caps (100 Places, 150 Routes, 200 map loads a day), add a $1 budget alert, and leave application restrictions off.

## App 1.16.1 - 2026-09-21
- Splash now plays the whole crown video (10 seconds, no cap, no tap-to-skip) and shows "KL Travel v<version>" under the video.

## App 1.16.0 - 2026-09-21
- New start-up splash: your rotating KL crown video plays full screen on black when the app opens (up to 5 seconds, tap to skip, no sound). Android 12+ phones now show a plain black system splash first so there is no white flash. If the video can't play, the app just opens.

## App 1.15.3 - 2026-09-21
- Seat map button now opens seatmaps.com instead of SeatGuru ("Seat map on SeatMaps"). Known planes (EVA, Philippine Airlines, Cebu Pacific, Cebgo) open their own seat map; any other plane opens that airline's seatmaps.com page; other airlines open seatmaps.com. No search pages.

## App 1.15.2 - 2026-09-21
- SeatGuru button now opens SeatGuru directly (no search). EVA 787-9, 787-10, A330-300 and A330-200, Philippine Airlines A321 and A320 open their own seat map; other planes open that airline's SeatGuru page (Cebu Pacific and Cebgo use the Cebu Pacific page).

## App 1.15.1 - 2026-09-21
- SeatGuru button: the search no longer starts with site: (it starts with seatguru.com).

## App 1.15.0 - 2026-09-20
- Receipts: Expenses > camera button. Take a photo (or pick one from the gallery); the app reads the merchant, date, total and currency on the phone, guesses the category, converts foreign amounts to USD, and lets you check and fix everything before saving. The photo is kept with the expense (tap a receipt row to view it).
- Expenses now show spending by category and the original currency and amount for foreign receipts.
- Local database is now version 5 (existing expenses are kept).
- The app file is bigger (about 28 MB) because the receipt reader is built in, so it works with no signal.

## App 1.14.0 - 2026-09-20
- Settings > Backup and restore: Back up saves your keys, lounge memberships, alert settings, trips (names and sheet links), saved travel modes and expenses to one file; Restore loads it after a reinstall or on a new phone.

## App 1.13.1 - 2026-09-20
- Lounges: the list now shows only the lounges your memberships match, by default. A tappable All lounges chip shows every lounge, with a warning that access may not be given at the others.

## App 1.13.0 - 2026-09-20
- Meals now show on the Activities tab (Activities = everything except transport and lodging).

## App 1.12.1 - 2026-09-20
- Settings > Google Maps key: every how-to step now has tappable links that open the exact Google Cloud page (create project, billing, each API's Enable page, your keys). Same for the API is blocked fix steps.

## App 1.12.0 - 2026-09-20
- New Transportation tab: flights, boats, ferries, trains, buses, drives and transfers (by the Type column), each with flight number, seat and plane when known.
- New Activities tab: activities, tours, wedding events and other things that are not transport, meals or lodging.
- Bottom bar is now Timeline, Transport, Activities, Lodging, More. Expenses, Tools and Settings moved under More.

## App 1.11.2 - 2026-09-20
- Removed the Business / first class ticket option from the lounge card (domestic first class does not include lounge access). Lounges now match only the programs you tick in Settings. The Cabin column is still shown as information.

## App 1.11.1 - 2026-09-20
- Settings > Google Maps key: new Fix: API is blocked error steps (enable Places API (New), allow it on the key, billing, wait). The key steps now say lounges need Places API (New).

## App 1.11.0 - 2026-09-20
- Flight page: new "Seat map on SeatGuru" button that opens the SeatGuru map for that airline and plane (uses the Aircraft column). The airline's own page is now labelled "Change seat on ...".

## App 1.10.1 - 2026-09-20
- App icon (thumbnail) is now the KL crown logo.

## App 1.10.0 - 2026-09-20
- Multiple trips: a trip picker screen (tap the trip name at the top of the timeline, or Settings > Switch or add a trip). Each trip has its own Google Sheet, timeline, lodging, expenses and weather; add, rename, change the sheet, or delete trips. Your existing sheet becomes "My trip" with everything kept.
- Alerts and the home-screen widget follow the trip that is open.
- Local database is now version 4.

## App 1.9.0 - 2026-09-20
- Splash screen with the KL crown logo (Android 12+ system splash plus an in-app one, tap to skip).
- Version numbers: shown in Settings > About and on the splash; files are named with the version.
- Sheet template now carries a version stamp (2.2).

## App 1.8.0 - 2026-09-20
- Business / first class ticket chip on the lounge card switches on automatically from the new Cabin column.

## App 1.7.0 - 2026-09-20
- Hotels renamed Lodging (a tab named Hotels still works).
- Settings button to get a new Google Sheet template.

## App 1.6.0 - 2026-09-20
- Airport lounges with map, navigation and Settings > Lounge access.

## App 1.5.0 - 2026-09-20
- Seat, plane type and airline seat-map link on flights.

## App 1.4.0 - 2026-09-20
- Photos of lodging (Google Places).

## App 1.3.0 - 2026-09-20
- Google Maps key can be entered in Settings, with in-app instructions; web-based route map.

## App 1.2.0 - 2026-09-20
- Fix: lodging confirmation numbers with letters were dropped by Google's data feed; sheet is now read through the CSV export.

## App 1.1.0 - 2026-09-20
- Currency converter, weather per day, home-screen widget, secure passport/booking document vault.

## App 1.0.0
- First version: timeline from your Google Sheet, flight and traffic alerts, routes with Drive / Rideshare / Transit, lodging info, expenses.

## Template 2.7 - 2026-09-21
- Columns are sized to fit their headers (the filter arrow no longer hides "Start Time", "Check-Out Date" and the like) and the long-text columns (Title, Address, Notes) wrap.
- Google Sheets copy: a small Apps Script re-fits the columns to their content every time the sheet is opened or edited (simple triggers, so it needs no approval). Widest column is capped so a long note wraps instead of stretching the sheet. Excel and the downloaded file cannot do this on their own; there use select all, Resize columns, Fit to data.
- Applied to the hosted Google template only. The Philippines 2027 and Alfredo's Wedding sheets are not changed.

## Template 2.6 - 2026-09-21
- Color coding. Every Events row is colored across the whole row by its Type: Flight blue, Boat/Ferry/Cruise teal, ground transport (Transport, Train, Bus, Drive, Taxi, Rideshare, Van...) yellow, Lodging purple, Meal orange, Wedding pink, Activity/Tour/Event green. Other types stay white. Rows on the Lodging tab are purple. It is a conditional-format rule, so a new row colors itself from its Type.
- Space between days: the how-to tab now says to leave two empty rows between one day and the next (the app ignores empty rows). A How-to legend shows the colors.
- Header row is now the same blue across every column (Seat, Aircraft and Cabin included).
- Also applied by hand to the hosted Google Sheet copy, and to the Philippines 2027 and Alfredo's Wedding trip sheets.

## Template 2.5 - 2026-09-21
- Seat column now documents several travelers: "Luis 32A / Sabrina 32B" (name, then seat, people separated by a slash). Note on the Seat header, updated how-to text and example row. Seat-map wording changed from SeatGuru to SeatMaps.

## Template 2.4 - 2026-09-20
- How to use text: meals are on the Activities tab.

## Template 2.3 - 2026-09-20
- Type dropdown now includes Boat, Ferry, Train, Bus, Drive, Tour and Wedding (used by the app's Transport and Activities tabs). How to use text updated; Cabin no longer switches lounge access.

## Template 2.2 - 2026-09-20
- Version stamp on the How to use tab and in the file properties.

## Template 2.1 - 2026-09-20
- Added the Cabin column (Events).

## Template 2.0 - 2026-09-20
- Hotels tab renamed Lodging. Added Seat and Aircraft columns. Confirmation, seat and phone columns formatted as text. How to use tab.

## Template 1.0
- Original Events and Hotels template (KLTravel_Template_v1.0.xlsx).
