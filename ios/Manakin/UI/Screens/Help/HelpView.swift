import SwiftUI

struct HelpView: View {
    var onReplayTutorial: () -> Void = {}

    @Environment(\.appColors) private var colors
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                // Replay tutorial card
                Button(action: onReplayTutorial) {
                    HStack(spacing: 12) {
                        Image(systemName: "play.fill")
                            .foregroundColor(.primary)
                        VStack(alignment: .leading) {
                            Text("Replay Tutorial")
                                .font(.system(size: 15, weight: .semibold))
                                .foregroundColor(.primary)
                            Text("Walk through the app basics again")
                                .font(.system(size: 13))
                                .foregroundColor(colors.onSurfaceVariant)
                        }
                        Spacer()
                    }
                    .padding(16)
                    .background(Color.primary.opacity(0.1))
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                }
                .buttonStyle(.plain)

                helpSection(
                    title: "What is Manakin?",
                    body: "Manakin helps you explore what species are active near you right now. It uses community science data from iNaturalist to show phenology \u{2014} when species are most likely to be observed throughout the year. Think of it like Merlin for all of nature, not just birds."
                )

                helpSection(
                    title: "Navigation",
                    body: "Manakin has four main tabs at the bottom:\n\n\u{2022} Explore \u{2014} browse species sorted by activity, search, and filter\n\u{2022} Targets \u{2014} your starred species, lifers, and new-for-area targets\n\u{2022} Datasets \u{2014} manage your downloaded data packs\n\u{2022} Settings \u{2014} appearance, notifications, and iNaturalist account"
                )

                helpSection(
                    title: "Data Packs",
                    body: "Manakin works with data packs \u{2014} collections of species data for a specific taxonomic group in a specific location.\n\nTo add a new data pack, go to the Datasets tab and tap the green \"+\" button:\n1. Search for one or more locations (e.g., \"Connecticut\", \"Colorado\")\n2. Search for one or more taxa (e.g., \"Butterflies\", \"Amphibians\", \"Snakes\")\n3. Give it a label (e.g., \"Herps\", \"Trip Species\")\n4. Set minimum observations \u{2014} higher values give fewer but more reliable species\n5. Tap Generate and wait \u{2014} this fetches data from iNaturalist\n\nYou can have multiple packs and select them from the dataset selector at the top of the Explore and Targets tabs. Tap a row to switch, or tap the checkbox to view multiple packs at once."
                )

                helpSection(
                    title: "Explore Tab",
                    body: "The main species list. Use the Active/All toggle in the top bar to show only currently active species or everything.\n\nEach species card shows:\n\u{2022} A blue checkmark after the name if you've observed it\n\u{2022} A colored rarity dot (green = common, orange = uncommon, red = rare)\n\u{2022} A status badge: Peak (amber), Active (green), Early/Late (blue), Inactive (gray)\n\u{2022} Activity percentage relative to peak\n\u{2022} A mini bar chart showing the full year's phenology\n\nSwipe right on any card to add it to your targets.\nLong-press a card to share the iNaturalist link.\n\nSort by Likelihood (combines activity + observation frequency), Peak Date, Name, or Taxonomy. Taxonomy sort shows group headers that adapt to your dataset."
                )

                helpSection(
                    title: "Targets Tab",
                    body: "Your species planning hub with three modes:\n\n\u{2022} Starred \u{2014} species you've manually starred by swiping right\n\u{2022} New for Area \u{2014} active species you haven't observed in the dataset's location\n\u{2022} Lifer Targets \u{2014} active species you've never observed anywhere\n\nUse the Active/All toggle and dataset selector to filter. Swipe left on starred species to remove them.\n\nThe New for Area and Lifer Targets modes require connecting your iNaturalist account in Settings."
                )

                helpSection(
                    title: "Date Picker & Trip Planning",
                    body: "Tap \"Today\" in the Explore tab to pick a different date or date range. The species list recalculates to show what will be active during that period.\n\nFor trip planning:\n1. Pick a date range for your trip\n2. Switch to the Targets tab > Lifer Targets or New for Area\n3. Select the dataset for your destination\n4. See exactly what you could find!"
                )

                helpSection(
                    title: "Observation Tracking",
                    body: "Connect your iNaturalist account to see what you've already observed:\n\n1. Go to Settings\n2. Enter your iNaturalist username\n3. Tap \"Sync Observations\"\n\nOnce synced, you'll see blue checkmarks next to observed species and observation counts in the Explore tab. The Targets tab unlocks New for Area and Lifer Targets modes.\n\nYour data is cached locally for offline use. Re-sync anytime to pick up new observations."
                )

                helpSection(
                    title: "Compare Locations",
                    body: "Access from the three-dot menu in the Explore tab. Pick two datasets to see species unique to each location and species in common. Great for deciding where to go on a trip."
                )

                helpSection(
                    title: "Organism of the Day",
                    body: "Tap the Manakin logo at the top of the Explore tab to discover a random active species from your packs. Changes daily."
                )

                helpSection(
                    title: "Notifications",
                    body: "Enable in Settings:\n\n\u{2022} Weekly Digest \u{2014} a summary of species entering peak and newly active species\n\u{2022} Target Species Alerts \u{2014} notifications when your starred species approach peak (2 weeks before) and reach peak\n\nChoose which day of the week to receive your digest."
                )

                helpSection(
                    title: "About the Data",
                    body: "All data comes from iNaturalist (inaturalist.org), a community science platform. Phenology is based on research-grade observations. Likelihood combines current activity with overall observation frequency. Rarity is relative to each dataset (bottom 25% = Rare, middle 50% = Uncommon, top 25% = Common). Only Creative Commons licensed photos are included."
                )

                Spacer().frame(height: 32)
            }
            .padding(16)
        }
        .background(colors.background)
        .navigationBarBackButtonHidden(true)
        .toolbar {
            ToolbarItem(placement: .navigationBarLeading) {
                Button { dismiss() } label: {
                    Image(systemName: "chevron.left")
                        .foregroundColor(.primary)
                }
            }
            ToolbarItem(placement: .principal) {
                Text("How to Use Manakin")
                    .font(.system(size: 17, weight: .bold))
                    .foregroundColor(.primary)
            }
        }
    }

    private func helpSection(title: String, body: String) -> some View {
        DisclosureGroup {
            Text(body)
                .font(.system(size: 14))
                .foregroundColor(colors.onSurface)
                .lineSpacing(4)
                .padding(.top, 8)
        } label: {
            Text(title)
                .font(.system(size: 17, weight: .semibold))
                .foregroundColor(colors.onBackground)
        }
        .padding(12)
        .background(colors.surface)
        .clipShape(RoundedRectangle(cornerRadius: 8))
        .tint(colors.onSurfaceVariant)
    }
}
