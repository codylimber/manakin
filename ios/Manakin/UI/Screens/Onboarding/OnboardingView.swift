import SwiftUI
import AVFoundation

struct OnboardingPage {
    let title: String
    let description: String
    let emoji: String
}

private let onboardingPages = [
    OnboardingPage(
        title: "Welcome to Manakin",
        description: "Your field companion for exploring species phenology \u{2014} discover what's active near you, right now.",
        emoji: "\u{1F426}"
    ),
    OnboardingPage(
        title: "Data Packs",
        description: "Go to the Datasets tab and tap + to download species data for any location and taxonomic group from iNaturalist.\n\nYou can combine multiple locations and taxa in one pack. Each pack contains phenology data showing when species are most likely to be observed.",
        emoji: "\u{1F4E6}"
    ),
    OnboardingPage(
        title: "Explore Species",
        description: "The Explore tab shows species sorted by likelihood. Use the Active/All toggle to filter.\n\nSwipe right on any species to add it to your targets. Long-press to share.",
        emoji: "\u{1F4C5}"
    ),
    OnboardingPage(
        title: "Targets & Tracking",
        description: "The Targets tab is your planning hub. Star species, find lifers, and discover species new to an area.\n\nConnect your iNaturalist username in Settings to unlock observation tracking.",
        emoji: "\u{2B50}"
    ),
    OnboardingPage(
        title: "Plan Ahead",
        description: "Use the date picker to see what will be active during a future trip. Pick a date range for multi-day planning.\n\nCompare locations from the menu to find species unique to different areas.",
        emoji: "\u{1F5FA}\u{FE0F}"
    )
]

struct OnboardingView: View {
    let onComplete: () -> Void
    var isReplay: Bool = false

    @State private var currentPage = 0
    @State private var audioPlayer: AVAudioPlayer?

    var body: some View {
        VStack(spacing: 0) {
            Spacer().frame(height: 48)

            TabView(selection: $currentPage) {
                ForEach(Array(onboardingPages.enumerated()), id: \.offset) { index, page in
                    pageContent(page: page, index: index)
                        .tag(index)
                }
            }
            .tabViewStyle(.page(indexDisplayMode: .never))

            // Page indicators
            HStack(spacing: 8) {
                ForEach(0..<onboardingPages.count, id: \.self) { index in
                    Circle()
                        .fill(index == currentPage ? Color.primary : Color.gray.opacity(0.3))
                        .frame(width: index == currentPage ? 10 : 8, height: index == currentPage ? 10 : 8)
                }
            }
            .padding(.vertical, 16)

            Spacer().frame(height: 16)

            // Buttons
            if currentPage == onboardingPages.count - 1 {
                Button(action: onComplete) {
                    Text(isReplay ? "Done" : "Get Started")
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .frame(height: 48)
                        .background(Color.primary)
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                }
            } else {
                HStack {
                    Button(action: onComplete) {
                        Text(isReplay ? "Close" : "Skip")
                            .foregroundColor(.gray)
                    }
                    Spacer()
                    Button {
                        withAnimation {
                            currentPage += 1
                        }
                    } label: {
                        Text("Next")
                            .font(.system(size: 16, weight: .semibold))
                            .foregroundColor(.white)
                            .padding(.horizontal, 24)
                            .padding(.vertical, 10)
                            .background(Color.primary)
                            .clipShape(RoundedRectangle(cornerRadius: 12))
                    }
                }
            }

            Spacer().frame(height: 16)
        }
        .padding(.horizontal, 24)
        .onAppear {
            playManakinCall()
        }
    }

    private func pageContent(page: OnboardingPage, index: Int) -> some View {
        VStack(spacing: 0) {
            Spacer()

            if index == 0 {
                // Logo placeholder - use app icon or text
                Image(systemName: "bird.fill")
                    .font(.system(size: 60))
                    .foregroundColor(.primary)
            } else {
                Text(page.emoji)
                    .font(.system(size: 64))
            }

            Spacer().frame(height: 32)

            Text(page.title)
                .font(.system(size: 24, weight: .bold))
                .foregroundColor(.primary)
                .multilineTextAlignment(.center)

            Spacer().frame(height: 16)

            Text(page.description)
                .font(.system(size: 15))
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
                .lineSpacing(4)
                .padding(.horizontal, 16)

            Spacer()
        }
    }

    private func playManakinCall() {
        guard let url = Bundle.main.url(forResource: "manakin_call", withExtension: "mp3") else { return }
        do {
            audioPlayer = try AVAudioPlayer(contentsOf: url)
            audioPlayer?.volume = 0.5
            audioPlayer?.play()
        } catch {
            // Sound file not found, skip silently
        }
    }
}
