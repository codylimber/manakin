import SwiftUI

struct AboutView: View {
    @Environment(\.appColors) private var colors
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                // App info card
                VStack(spacing: 8) {
                    Text("Manakin")
                        .font(.system(size: 24, weight: .bold))
                        .foregroundColor(.appPrimary)
                    Text("Version 1.2")
                        .font(.system(size: 13))
                        .foregroundColor(colors.onSurfaceVariant)
                    Text("A field companion for exploring species phenology \u{2014} what's active near you, right now.")
                        .font(.system(size: 14))
                        .foregroundColor(colors.onSurface)
                        .multilineTextAlignment(.center)
                }
                .frame(maxWidth: .infinity)
                .padding(16)
                .background(colors.surface)
                .clipShape(RoundedRectangle(cornerRadius: 12))

                // Data Source
                Text("Data Source")
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundColor(colors.onBackground)

                Text("Manakin uses data from iNaturalist (inaturalist.org), a joint initiative of the California Academy of Sciences and the National Geographic Society. iNaturalist is a community science platform where people share observations of organisms from around the world.\n\nAll species data, observation counts, and phenology patterns are derived from research-grade observations submitted by the iNaturalist community. Manakin is not affiliated with or endorsed by iNaturalist.")
                    .font(.system(size: 14))
                    .foregroundColor(colors.onSurface)
                    .lineSpacing(4)

                Button {
                    if let url = URL(string: "https://www.inaturalist.org") {
                        UIApplication.shared.open(url)
                    }
                } label: {
                    Text("Visit iNaturalist.org")
                        .font(.system(size: 14))
                        .foregroundColor(.appPrimary)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 10)
                        .overlay(
                            RoundedRectangle(cornerRadius: 8)
                                .stroke(Color.appPrimary.opacity(0.5), lineWidth: 1)
                        )
                }

                // Photo Licensing
                Text("Photo Licensing")
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundColor(colors.onBackground)

                Text("Species photos are sourced from iNaturalist and are used under Creative Commons licenses. Only photos with CC licenses (CC0, CC-BY, CC-BY-NC, CC-BY-SA, etc.) are downloaded \u{2014} photos marked \"All Rights Reserved\" are excluded.\n\nIndividual photo attributions are displayed on each photo. The original photographers retain all rights to their images under the terms of their chosen license.")
                    .font(.system(size: 14))
                    .foregroundColor(colors.onSurface)
                    .lineSpacing(4)

                // API Usage
                Text("API Usage")
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundColor(colors.onBackground)

                Text("Manakin accesses the iNaturalist API v1 to fetch species data. The app respects iNaturalist's rate limiting guidelines, spacing requests at 2-second intervals to avoid overloading the service. Data is cached locally after download so repeated access doesn't require additional API calls.")
                    .font(.system(size: 14))
                    .foregroundColor(colors.onSurface)
                    .lineSpacing(4)

                // Acknowledgments
                Text("Acknowledgments")
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundColor(colors.onBackground)

                Text("Species descriptions are sourced from Wikipedia and are available under the Creative Commons Attribution-ShareAlike License.\n\nNamed after the manakin \u{2014} a family of small, colorful birds known for their elaborate courtship displays.")
                    .font(.system(size: 14))
                    .foregroundColor(colors.onSurface)
                    .lineSpacing(4)

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
                        .foregroundColor(.appPrimary)
                }
            }
            ToolbarItem(placement: .principal) {
                Text("About Manakin")
                    .font(.system(size: 17, weight: .bold))
                    .foregroundColor(.appPrimary)
            }
        }
    }
}
